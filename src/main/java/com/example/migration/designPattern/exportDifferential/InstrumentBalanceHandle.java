package com.example.migration.designPattern.exportDifferential;

import com.alibaba.excel.util.StringUtils;
import com.example.migration.dao.master.entity.*;
import com.example.migration.dao.master.mapper.CmsViewMapper;
import com.example.migration.dao.master.mapper.SpInstrumentBalanceClosingAsAtMapper;
import com.example.migration.dao.master.mapper.TTLMQOrdersMapper;
import com.example.migration.dao.master.mapper.TTLMarketBoardMapper;
import com.example.migration.dao.slave.entity.Vcbaccount;
import com.example.migration.dao.slave.entity.Vcbtradingacc;
import com.example.migration.dao.slave.entity.VcbtradingaccExample;
import com.example.migration.dao.slave.mapper.ScdummyMapper;
import com.example.migration.dao.slave.mapper.VcbtradingaccMapper;
import com.example.migration.designPattern.ExportDifferentialStrategy;
import com.example.migration.enumeration.export.AcctTypeEnum;
import com.example.migration.enumeration.export.BalancetypeEnum;
import com.example.migration.enumeration.export.CcyTypeEnum;
import com.example.migration.enumeration.export.DataSourceTypeEnum;
import com.example.migration.pojo.export.req.CashExportReq;
import com.example.migration.pojo.export.vo.CashBalanceSQLVo;
import com.example.migration.pojo.export.vo.ExportTransferVo;
import com.example.migration.pojo.export.vo.InstrumentSQLVo;
import com.example.migration.pojo.export.vo.SpCashBalanceVo;
import org.apache.ibatis.session.RowBounds;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * InstrumentBalance
 */
@Component
public class InstrumentBalanceHandle implements ExportDifferentialStrategy {

    @Autowired
    private SpInstrumentBalanceClosingAsAtMapper spInstrumentBalanceClosingAsAtMapper;

    @Autowired
    private CmsViewMapper cmsViewMapper;

    @Autowired
    private TTLMarketBoardMapper ttlMarketBoardMapper;

    @Autowired
    private VcbtradingaccMapper vcbtradingaccMapper;

    @Autowired
    private TTLMQOrdersMapper ttlMQOrdersMapper;

    @Autowired
    private ScdummyMapper scdummyMapper;


    @Override
    public DataSourceTypeEnum getDataSourceType() {
        return DataSourceTypeEnum.Instrument;
    }

    @Override
    public Map<String, List<ExportTransferVo>> queryExportDifferentialData(CashExportReq req) {

        Map<String, List<ExportTransferVo>> map = new HashMap<>();
        // 查询基础数据
        List<ExportTransferVo> balanceVos = handleDataBase(req);
        List<ExportTransferVo> sameList = new ArrayList<>();
        List<ExportTransferVo> abnormalList = new ArrayList<>();
        List<ExportTransferVo> exclusiveList = new ArrayList<>();
        instrumentDataCollectionProcess(balanceVos, sameList, abnormalList, exclusiveList,req);

        setErrorReason(abnormalList,req);
        //ttl特有的
        List<ExportTransferVo> ttlList = ttlDataCollectionProcess(balanceVos,req);
        map.put("sameList", sameList);  //数据一致
        map.put("abnormalList", abnormalList);  //数据异常
        map.put("exclusiveList", exclusiveList);   //存储过程特有
        map.put("ttlList", ttlList);   //ttl特有
        return map;
    }

    private void setErrorReason(List<ExportTransferVo> abnormalList, CashExportReq req) {
        if (req.getBalanceType().equals(BalancetypeEnum.D)) {
            return;
        }

        int batchSize = 1000;
        int threadCount = Runtime.getRuntime().availableProcessors(); // 获取可用核心数作为线程数
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        List<Future<?>> futures = new ArrayList<>();

        try {
            // 按批次提交任务
            for (int i = 0; i < abnormalList.size(); i += batchSize) {
                int endIndex = Math.min(i + batchSize, abnormalList.size());
                List<ExportTransferVo> batch = abnormalList.subList(i, endIndex);

                Future<?> future = executorService.submit(() -> {
                    for (ExportTransferVo vo : batch) {
                        // 根据 BalanceType 动态处理
                        processBalanceType(req.getBalanceType(), vo, req);
                    }
                });
                futures.add(future);
            }

            // 等待所有任务完成
            for (Future<?> future : futures) {
                future.get(); // 阻塞直到任务完成
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to process setErrorReason in parallel", e);
        } finally {
            executorService.shutdown(); // 关闭线程池
        }
    }

    /**
     * 根据 BalanceType 处理不同的逻辑
     */
    private void processBalanceType(BalancetypeEnum balanceType, ExportTransferVo vo, CashExportReq req) {
        switch (balanceType) {
            case L:
                handleLedgerBalance(vo, req);
                break;
            case O:
                handleOtherBalance(vo, req);
                break;
            default:
                throw new IllegalArgumentException("Unsupported BalanceType: " + balanceType);
        }
    }

    /**
     * 处理 L 类型的逻辑
     */
    private void handleLedgerBalance(ExportTransferVo vo, CashExportReq req) {
        Vcbtradingacc info = vo.getVcbtradingaccInfo();
        BigDecimal total = calculateTotalFromVcbtradingacc(info);
        if (vo.getBalance().compareTo(total) == 0) {
            vo.setReason("Timegap");
            return;
        }
        BigDecimal sum = calculateSumFromMQOrders(req, vo);
        if (vo.getBalance().compareTo(sum) == 0) {
            vo.setReason("MQTimegap");
            return;
        }
        checkSqlAndSetReason(vo, req);
    }

    /**
     * 计算 Vcbaccount 中指定字段的总和（绝对值）
     */
    private BigDecimal calculateTotalFromVcbtradingacc(Vcbtradingacc info) {
        return Optional.ofNullable(info)
                .map(i -> i.getTinactivebuy().abs()
                        .add(i.getTinactivesell().abs())
                        .add(i.getTtodaybuy().abs())
                        .add(i.getTtodaysell().abs())
                        .add(i.getTtodayconfirmbuy().abs())
                        .add(i.getTtodayconfirmsell().abs()))
                .orElse(BigDecimal.ZERO);
    }

    /**
     * 处理 O 类型的逻辑
     */
    private void handleOtherBalance(ExportTransferVo vo, CashExportReq req) {
        checkSqlAndSetReason(vo, req);
    }

    /**
     * 计算 MQOrders 中 CashLedgerDelta 字段的总和（绝对值）
     */
    private BigDecimal calculateSumFromMQOrders(CashExportReq req, ExportTransferVo vo) {
        TTLMQOrdersExample ttlmqOrdersExample = new TTLMQOrdersExample();
        TTLMQOrdersExample.Criteria criteria = ttlmqOrdersExample.createCriteria();
        criteria.andMqstatusNotEqualTo("Fail");
        criteria.andInstrumentidEqualTo(vo.getInstrument());
        criteria.andMqdatetimeGreaterThanOrEqualTo(req.getMqDate());
        criteria.andSubaccountidLike("%" + vo.getClntCode() + "%");

        return ttlMQOrdersMapper.selectByExample(ttlmqOrdersExample).stream()
                .map(TTLMQOrders::getInstledgerdelta)
                .filter(Objects::nonNull)
                .map(BigDecimal::new)
                .map(BigDecimal::abs)
                .reduce(BigDecimal::add)
                .orElse(BigDecimal.ZERO);
    }

    /**
     * 查询 SQL 并设置 Reason
     */
    private void checkSqlAndSetReason(ExportTransferVo vo, CashExportReq req) {
        if (StringUtils.isEmpty(req.getSqlInfo())) {
            return;
        }
        List<InstrumentSQLVo> sqlList = scdummyMapper.queryInstrumentSqlList(req.getSqlInfo());
        List<InstrumentSQLVo> filteredSqlList = sqlList.stream()
                .filter(e -> e.getClientid().equals(vo.getClntCode())
                        && e.getTtlmarketid().equals(vo.getMarketId())
                        && e.getInstrument().equals(vo.getInstrument())
                        && e.getIssueamt().abs().compareTo(vo.getBalance()) == 0)
                .collect(Collectors.toList());

        if (!filteredSqlList.isEmpty()) {
            vo.setReason(filteredSqlList.get(0).getRem());
        }
    }

    /**
     * ttl数据处理
     **/
    private List<ExportTransferVo> ttlDataCollectionProcess(List<ExportTransferVo> balanceVos,CashExportReq req) {
        List<ExportTransferVo> ttlList = new ArrayList<>();
        List<Vcbtradingacc> vcbtradingaccs = fetchVcbtradingaccPaginated();
        Map<String, ExportTransferVo> voMap = balanceVos.stream()
                .collect(Collectors.toMap(
                                e -> e.getClntCode() + e.getInstrument() + e.getMarketId() + e.getAccounts(),
                                Function.identity(),
                                (a, b) -> a
                        )
                );

        List<Vcbtradingacc> vcbtradingaccList = new ArrayList<>();
        for (Vcbtradingacc vcbtradingacc : vcbtradingaccs) {
            String key = vcbtradingacc.getClientid().trim() + vcbtradingacc.getInstrumentid().trim() + vcbtradingacc.getMarketid().trim() + vcbtradingacc.getTradingaccseq();
            if (!voMap.containsKey(key)) {
                vcbtradingaccList.add(vcbtradingacc);
            }
        }

        for (Vcbtradingacc vcbtradingacc : vcbtradingaccList) {
            ExportTransferVo vo = new ExportTransferVo();
            vo.setClientid(vcbtradingacc.getClientid());
            vo.setInstrumentid(vcbtradingacc.getInstrumentid());
            vo.setTtlMarketId(vcbtradingacc.getMarketid());
            vo.setTradingaccseq(vcbtradingacc.getTradingaccseq());
            if (req.getBalanceType().equals(BalancetypeEnum.L)) {
                vo.setLedgerqty(vcbtradingacc.getLedgerqty());
            }else if (req.getBalanceType().equals(BalancetypeEnum.O)){
                vo.setTtlLedgerbal(vcbtradingacc.getTsettled());
            }else if (req.getBalanceType().equals(BalancetypeEnum.D)) {
                vo.setTtlLedgerbal(vcbtradingacc.getTmanualhold());
            }
            ttlList.add(vo);
        }
        return ttlList;
    }

    /**
     * 分页查询 Vcbtradingacc 表数据，每次查询1000条并收集
     * @return 所有 Vcbtradingacc 数据
     */
    private List<Vcbtradingacc> fetchVcbtradingaccPaginated() {
        List<Vcbtradingacc> result = new ArrayList<>();
        int pageSize = 1000;
        int totalRecords = (int) vcbtradingaccMapper.countByExample(null); // 获取总记录数
        int totalPages = (int) Math.ceil((double) totalRecords / pageSize); // 计算总页数

        int threadCount = Runtime.getRuntime().availableProcessors(); // 获取可用核心数作为线程数
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        List<Future<List<Vcbtradingacc>>> futures = new ArrayList<>();

        try {
            // 提交任务到线程池
            for (int page = 0; page < totalPages; page++) {
                final int currentPage = page;
                Callable<List<Vcbtradingacc>> task = () -> {
                    RowBounds rowBounds = new RowBounds(currentPage * pageSize, pageSize);
                    return vcbtradingaccMapper.selectByExampleWithLimit(rowBounds);
                };
                Future<List<Vcbtradingacc>> future = executorService.submit(task);
                futures.add(future);
            }

            // 收集所有线程的结果
            for (Future<List<Vcbtradingacc>> future : futures) {
                try {
                    result.addAll(future.get()); // 等待任务完成并获取结果
                } catch (Exception e) {
                    throw new RuntimeException("Failed to fetch Vcbtradingacc data", e);
                }
            }
        } finally {
            executorService.shutdown(); // 关闭线程池
        }

        return result;
    }

    /**
     * 数据收集逻辑
     * @param balanceVos
     * @param sameList 数据一致
     * @param abnormalList 数据异常
     * @param exclusiveList 存储过程特有的
     * @return
     */
    private void instrumentDataCollectionProcess(List<ExportTransferVo> balanceVos,
                                                 List<ExportTransferVo> sameList,
                                                 List<ExportTransferVo> abnormalList,
                                                 List<ExportTransferVo> exclusiveList,
                                                 CashExportReq req) {
        List<Vcbtradingacc> vcbtradingaccs = fetchVcbtradingaccInParallel(balanceVos);
        Map<String, Vcbtradingacc> vcbtradingaccMap = vcbtradingaccs.stream()
                .collect(Collectors.toMap(
                        e -> e.getClientid().trim() + e.getInstrumentid().trim() + e.getMarketid().trim() + e.getTradingaccseq(),
                        Function.identity(),
                        (a, b) -> a
                ));
        for (ExportTransferVo vo : balanceVos) {
            String key = vo.getClntCode().trim() + vo.getInstrument().trim() + vo.getMarketId().trim() + vo.getAccounts();
            if (vcbtradingaccMap.containsKey(key)) {
                Vcbtradingacc vcbtradingacc = vcbtradingaccMap.get(key);
                vo.setClientid(vcbtradingacc.getClientid());
                vo.setInstrumentid(vcbtradingacc.getInstrumentid());
                vo.setTtlMarketId(vcbtradingacc.getMarketid());
                vo.setTradingaccseq(vcbtradingacc.getTradingaccseq());
                // 根据 BalanceType 动态处理
                processBalanceType(req.getBalanceType(), vo, vcbtradingacc, sameList, abnormalList);
            }else {
                exclusiveList.add(vo);
            }
        }
    }

    private void processBalanceType(BalancetypeEnum balanceType, ExportTransferVo vo, Vcbtradingacc vcbtradingacc, List<ExportTransferVo> sameList, List<ExportTransferVo> abnormalList) {
        BigDecimal ttlValue;
        switch (balanceType) {
            case L:
                ttlValue = vcbtradingacc.getLedgerqty();
                break;
            case D:
                ttlValue = vcbtradingacc.getTmanualhold();
                break;
            case O:
                ttlValue = vcbtradingacc.getTsettled();
                break;
            default:
                throw new IllegalArgumentException("Unsupported BalanceType: " + balanceType);
        }
        vo.setLedgerqty(ttlValue);
        // 统一比较逻辑
        if (ttlValue.abs().compareTo(vo.getBalance().abs()) == 0) {
            vo.setReason("Match");
            sameList.add(vo);
        } else {
            vo.setVcbtradingaccInfo(vcbtradingacc);
            abnormalList.add(vo);
        }
    }

    /**
     * 获取Vcbtradingacc表数据 多线程处理
     * @param balanceVos
     * @return
     */
    private List<Vcbtradingacc> fetchVcbtradingaccInParallel(List<ExportTransferVo> balanceVos) {
        List<Vcbtradingacc> result = new ArrayList<>();
        int batchSize = 1000;
        int threadCount = Runtime.getRuntime().availableProcessors(); // 获取可用核心数作为线程数
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        List<Future<List<Vcbtradingacc>>> futures = new ArrayList<>();
        for (int i = 0; i < balanceVos.size(); i += batchSize) {
            int endIndex = Math.min(i + batchSize, balanceVos.size());
            List<ExportTransferVo> voList = balanceVos.subList(i, endIndex);

            // 提取参数并去重
            List<String> clientIds = voList.stream()
                    .map(ExportTransferVo::getClntCode)
                    .filter(Objects::nonNull)
                    .distinct()
                    .collect(Collectors.toList());
            List<Integer> accountSeqs = voList.stream()
                    .map(ExportTransferVo::getAccounts)
                    .filter(Objects::nonNull)
                    .map(Integer::valueOf)
                    .distinct()
                    .collect(Collectors.toList());
            List<String> integers = voList.stream()
                    .map(ExportTransferVo::getInstrument)
                    .filter(Objects::nonNull)
                    .distinct()
                    .collect(Collectors.toList());
            List<String> marketIds = voList.stream()
                    .map(ExportTransferVo::getMarketId)
                    .filter(Objects::nonNull)
                    .distinct()
                    .collect(Collectors.toList());
            Callable<List<Vcbtradingacc>> task = () -> {
                VcbtradingaccExample example = new VcbtradingaccExample();
                VcbtradingaccExample.Criteria criteria = example.createCriteria();
                criteria.andClientidIn(clientIds);
                criteria.andTradingaccseqIn(accountSeqs);
                criteria.andMarketidIn(marketIds);
                criteria.andInstrumentidIn(integers);
                return vcbtradingaccMapper.selectByExample(example);
            };
            Future<List<Vcbtradingacc>> future = executorService.submit(task);
            futures.add(future);
        }
        // 收集所有线程的结果
        for (Future<List<Vcbtradingacc>> future : futures) {
            try {
                result.addAll(future.get()); // 等待任务完成并获取结果
            } catch (Exception e) {
                throw new RuntimeException("Failed to fetch Vcbtradingacc data", e);
            }
        }
        executorService.shutdown(); // 关闭线程池
        return result;
    }

    private List<ExportTransferVo> handleDataBase(CashExportReq req) {

        List<SpInstrumentBalanceClosingAsAt> spInstrumentBalanceClosingAsAts = spInstrumentBalanceClosingAsAtMapper.querySpInstrumentBalanceClosingAsAt(req);
        // 过滤掉 clntCode 首字母为 C 的数据
        spInstrumentBalanceClosingAsAts = spInstrumentBalanceClosingAsAts.stream()
                .filter(closingAsAt -> closingAsAt.getClntCode() != null &&
                        !closingAsAt.getClntCode().startsWith("C") && !closingAsAt.getClntCode().startsWith("c"))
                .peek(e -> {
                    e.setClntCode(e.getClntCode().trim()); // 先 trim 再设置
                })
                .collect(Collectors.toList());
        List<CmsView> cmsViews = fetchCmsViewsInParallel(spInstrumentBalanceClosingAsAts);
        Map<String, String> acctMap = cmsViews.stream()
                .collect(Collectors.toMap(CmsView::getAccountcode, CmsView::getDefaulttradingacc, (a, b) -> a));

        // 设置TTLMarketID、账户类型
        List<TTLMarketBoard> ttlMarketBoards = ttlMarketBoardMapper.selectByExample(null);
        Map<String, String> ttlMarketIDMap = ttlMarketBoards.stream()
                .collect(Collectors.toMap(TTLMarketBoard::getMarket, TTLMarketBoard::getTtlmarketid, (a, b) -> a));
        for (SpInstrumentBalanceClosingAsAt closingAsAt : spInstrumentBalanceClosingAsAts) {
            closingAsAt.setAccounts(acctMap.getOrDefault(closingAsAt.getClntCode(), ""));
            closingAsAt.setTtlMarketID(ttlMarketIDMap.getOrDefault(closingAsAt.getMarket(), ""));
        }
        Map<String, Map<String, Map<String, Map<String, List<SpInstrumentBalanceClosingAsAt>>>>> groupedData = spInstrumentBalanceClosingAsAts.stream()
                .collect(Collectors.groupingBy(
                        SpInstrumentBalanceClosingAsAt::getClntCode,
                        Collectors.groupingBy(
                                SpInstrumentBalanceClosingAsAt::getAccounts,
                                Collectors.groupingBy(
                                        SpInstrumentBalanceClosingAsAt::getTtlMarketID,
                                        Collectors.groupingBy(SpInstrumentBalanceClosingAsAt::getInstrument)
                                )
                        )
                ));
        List<ExportTransferVo> balanceVos = new ArrayList<>();
        for (Map.Entry<String, Map<String, Map<String, Map<String, List<SpInstrumentBalanceClosingAsAt>>>>> entry : groupedData.entrySet()) {
            if (entry.getValue().size() > 0) {
                for (Map.Entry<String, Map<String, Map<String, List<SpInstrumentBalanceClosingAsAt>>>> accountsEntry : entry.getValue().entrySet()) {
                    if (accountsEntry.getValue().size() > 0) {
                        for (Map.Entry<String, Map<String, List<SpInstrumentBalanceClosingAsAt>>> marketEntry : accountsEntry.getValue().entrySet()) {
                            if (marketEntry.getValue().size() > 0) {
                                for (Map.Entry<String, List<SpInstrumentBalanceClosingAsAt>> instrumentEntry : marketEntry.getValue().entrySet()) {
                                    SpInstrumentBalanceClosingAsAt closingAsAt = instrumentEntry.getValue().get(0);
                                    if (closingAsAt != null) {
                                        ExportTransferVo vo = new ExportTransferVo();
                                        vo.setClntCode(closingAsAt.getClntCode().trim());
                                        vo.setAccounts(closingAsAt.getAccounts().trim());
                                        vo.setMarketId(closingAsAt.getTtlMarketID().trim());
                                        vo.setInstrument(closingAsAt.getInstrument().trim());
                                        vo.setBalance(instrumentEntry.getValue().stream()
                                                .map(SpInstrumentBalanceClosingAsAt::getAsAt)
                                                .filter(Objects::nonNull) // 过滤空值
                                                .map(BigDecimal::abs)
                                                .reduce(BigDecimal.ZERO, BigDecimal::add));
                                        balanceVos.add(vo);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        for (ExportTransferVo balanceVo : balanceVos) {
            balanceVo.setAccounts(AcctTypeEnum.getByCode(balanceVo.getAccounts()));
        }
        return balanceVos;
    }

    private List<CmsView> fetchCmsViewsInParallel(List<SpInstrumentBalanceClosingAsAt> spInstrumentBalanceClosingAsAts) {
        List<CmsView> cmsViews = new ArrayList<>();
        List<String> accountCodes = spInstrumentBalanceClosingAsAts.stream()
                .map(SpInstrumentBalanceClosingAsAt::getClntCode)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());

        int batchSize = 1000;
        int threadCount = Runtime.getRuntime().availableProcessors(); // 获取可用核心数作为线程数
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        List<Future<List<CmsView>>> futures = new ArrayList<>();

        // 提交任务到线程池
        for (int i = 0; i < accountCodes.size(); i += batchSize) {
            int endIndex = Math.min(i + batchSize, accountCodes.size());
            List<String> batchAccountCodes = accountCodes.subList(i, endIndex);

            Callable<List<CmsView>> task = () -> {
                CmsViewExample example = new CmsViewExample();
                example.createCriteria().andAccountcodeIn(batchAccountCodes);
                return cmsViewMapper.selectByExample(example);
            };

            Future<List<CmsView>> future = executorService.submit(task);
            futures.add(future);
        }

        // 收集所有线程的结果
        for (Future<List<CmsView>> future : futures) {
            try {
                cmsViews.addAll(future.get()); // 等待任务完成并获取结果
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        executorService.shutdown(); // 关闭线程池
        return cmsViews;
    }
}
