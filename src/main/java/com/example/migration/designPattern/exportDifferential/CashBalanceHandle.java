package com.example.migration.designPattern.exportDifferential;

import com.alibaba.excel.util.StringUtils;
import com.example.migration.dao.master.entity.*;
import com.example.migration.dao.master.mapper.CmsViewMapper;
import com.example.migration.dao.master.mapper.SpCashBalanceClosingAsAtMapper;
import com.example.migration.dao.master.mapper.TTLMQOrdersMapper;
import com.example.migration.dao.slave.entity.Vcbaccount;
import com.example.migration.dao.slave.entity.VcbaccountExample;
import com.example.migration.dao.slave.mapper.ScdummyMapper;
import com.example.migration.dao.slave.mapper.VcbaccountMapper;
import com.example.migration.designPattern.ExportDifferentialStrategy;
import com.example.migration.enumeration.export.AcctTypeEnum;
import com.example.migration.enumeration.export.BalancetypeEnum;
import com.example.migration.enumeration.export.CcyTypeEnum;
import com.example.migration.enumeration.export.DataSourceTypeEnum;
import com.example.migration.pojo.export.req.CashExportReq;
import com.example.migration.pojo.export.vo.CashBalanceSQLVo;
import com.example.migration.pojo.export.vo.ExportTransferVo;
import com.example.migration.pojo.export.vo.SpCashBalanceVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * CashBalance
 * 现金账户数据处理
 */
@Component
public class CashBalanceHandle implements ExportDifferentialStrategy {

    @Autowired
    private SpCashBalanceClosingAsAtMapper spCashBalanceClosingAsAtMapper;

    @Autowired
    private CmsViewMapper cmsViewMapper;

    @Autowired
    private VcbaccountMapper vcbaccountMapper;

    @Autowired
    private TTLMQOrdersMapper ttlMQOrdersMapper;

    @Autowired
    private ScdummyMapper scdummyMapper;

    @Override
    public DataSourceTypeEnum getDataSourceType() {
        return DataSourceTypeEnum.Cash;
    }

    @Override
    public Map<String, List<ExportTransferVo>> queryExportDifferentialData(CashExportReq req) {
        Map<String, List<ExportTransferVo>> map = new HashMap<>();
        // 查询基础数据
        List<ExportTransferVo> balanceVos = handleDataBase(req);
        List<ExportTransferVo> sameList = new ArrayList<>();
        List<ExportTransferVo> abnormalList = new ArrayList<>();
        List<ExportTransferVo> exclusiveList = new ArrayList<>();
        octobackDataCollectionProcess(balanceVos, sameList, abnormalList, exclusiveList,req);

        setErrorReason(abnormalList,req);
        //ttl特有的
        List<ExportTransferVo> ttlList = ttlDataCollectionProcess(balanceVos,req);
        map.put("sameList", sameList);  //数据一致
        map.put("abnormalList", abnormalList);  //数据异常
        map.put("exclusiveList", exclusiveList);   //存储过程特有
        map.put("ttlList", ttlList);   //ttl特有
        return map;
    }

    /**
     * 数据不一致 需查询具体原因
     **/
    private void setErrorReason(List<ExportTransferVo> abnormalList, CashExportReq req) {
        if (req.getBalanceType().equals(BalancetypeEnum.D)) {
            return;
        }

        int threadCount = Runtime.getRuntime().availableProcessors(); // 获取可用核心数作为线程数
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);

        try {
            List<Future<?>> futures = new ArrayList<>();
            for (ExportTransferVo vo : abnormalList) {
                Future<?> future = executorService.submit(() -> {
                    // 根据 BalanceType 动态处理
                    processBalanceType(req.getBalanceType(), vo, req);
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
        Vcbaccount info = vo.getVcbaccountInfo();
        BigDecimal total = calculateTotalFromVcbaccount(info);
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
     * 处理 O 类型的逻辑
     */
    private void handleOtherBalance(ExportTransferVo vo, CashExportReq req) {
        checkSqlAndSetReason(vo, req);
    }

    /**
     * 计算 Vcbaccount 中指定字段的总和（绝对值）
     */
    private BigDecimal calculateTotalFromVcbaccount(Vcbaccount info) {
        return Optional.ofNullable(info)
                .map(i -> i.getCinactivebuy().abs()
                        .add(i.getCtodaybuy().abs())
                        .add(i.getCtodayconfirmbuy().abs())
                        .add(i.getCinactivesell().abs())
                        .add(i.getCtodaysell().abs())
                        .add(i.getCtodayconfirmsell().abs()))
                .orElse(BigDecimal.ZERO);
    }

    /**
     * 计算 MQOrders 中 CashLedgerDelta 字段的总和（绝对值）
     */
    private BigDecimal calculateSumFromMQOrders(CashExportReq req, ExportTransferVo vo) {
        TTLMQOrdersExample ttlmqOrdersExample = new TTLMQOrdersExample();
        TTLMQOrdersExample.Criteria criteria = ttlmqOrdersExample.createCriteria();
        criteria.andMqstatusNotEqualTo("Fail");
        criteria.andMqdatetimeGreaterThanOrEqualTo(req.getMqDate());
        criteria.andSubaccountidLike("%" + vo.getClntCode() + "%");

        return ttlMQOrdersMapper.selectByExample(ttlmqOrdersExample).stream()
                .map(TTLMQOrders::getCashledgerdelta)
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
        List<CashBalanceSQLVo> sqlList = scdummyMapper.queryCashBalanceSqlList(req.getSqlInfo());
        List<CashBalanceSQLVo> filteredSqlList = sqlList.stream()
                .filter(e -> e.getClientid().equals(vo.getClntCode())
                        && e.getCcy().equals(vo.getCcy())
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
        List<Vcbaccount> vcbaccounts = fetchVcbaccountsInParallel(balanceVos,"1",req.getBalanceType());

        for (Vcbaccount vcbaccount : vcbaccounts) {
            ExportTransferVo vo = new ExportTransferVo();
            vo.setTtlClientid(vcbaccount.getClientid());
            vo.setTtlCurrencyid(vcbaccount.getCurrencyid());
            vo.setTtlAccountseq(String.valueOf(vcbaccount.getAccountseq()));
            if (req.getBalanceType().equals(BalancetypeEnum.L)) {
                vo.setTtlLedgerbal(vcbaccount.getLedgerbal());
            }else if (req.getBalanceType().equals(BalancetypeEnum.O)){
                vo.setTtlLedgerbal(vcbaccount.getCsettled());
            }else if (req.getBalanceType().equals(BalancetypeEnum.D)) {
                vo.setTtlLedgerbal(vcbaccount.getCmanualhold());
            }
            ttlList.add(vo);
        }
        return ttlList;
    }

    /**
     * 数据收集逻辑
     * @param balanceVos
     * @param sameList 数据一致
     * @param abnormalList 数据异常
     * @param exclusiveList 存储过程特有的
     * @return
     */
    private void octobackDataCollectionProcess(List<ExportTransferVo> balanceVos,
                                                     List<ExportTransferVo> sameList,
                                                     List<ExportTransferVo> abnormalList,
                                                     List<ExportTransferVo> exclusiveList,
                                                     CashExportReq req) {

        List<Vcbaccount> vcbaccounts = fetchVcbaccountsInParallel(balanceVos, null,req.getBalanceType());
        Map<String, Vcbaccount> decimalMap = vcbaccounts.stream()
                .collect(Collectors.toMap(
                        e -> e.getClientid().trim() + e.getAccountseq() + e.getCurrencyid().trim(),
                        Function.identity(),
                        (a, b) -> a
                ));

        for (ExportTransferVo balanceVo : balanceVos) {
            String key = balanceVo.getClntCode() + balanceVo.getAccounts() + balanceVo.getCcy();
            if (decimalMap.containsKey(key)) {
                Vcbaccount vcbaccount = decimalMap.get(key);
                balanceVo.setTtlClientid(vcbaccount.getClientid());
                balanceVo.setTtlCurrencyid(vcbaccount.getCurrencyid());
                balanceVo.setTtlAccountseq(String.valueOf(vcbaccount.getAccountseq()));

                // 根据 BalanceType 动态处理
                processBalanceType(req.getBalanceType(), balanceVo, vcbaccount, sameList, abnormalList);
            } else {
                exclusiveList.add(balanceVo);
            }
        }
    }

    /**
     * 根据 BalanceType 处理不同的逻辑
     */
    private void processBalanceType(BalancetypeEnum balanceType,
                                    ExportTransferVo balanceVo,
                                    Vcbaccount vcbaccount,
                                    List<ExportTransferVo> sameList,
                                    List<ExportTransferVo> abnormalList) {
        BigDecimal ttlValue;
        switch (balanceType) {
            case L:
                ttlValue = vcbaccount.getLedgerbal();
                break;
            case D:
                ttlValue = vcbaccount.getCmanualhold();
                break;
            case O:
                ttlValue = vcbaccount.getCsettled();
                break;
            default:
                throw new IllegalArgumentException("Unsupported BalanceType: " + balanceType);
        }
        balanceVo.setTtlLedgerbal(ttlValue);

        // 统一比较逻辑
        if (ttlValue.abs().compareTo(balanceVo.getBalance().abs()) == 0) {
            balanceVo.setReason("Match");
            sameList.add(balanceVo);
        } else {
            balanceVo.setVcbaccountInfo(vcbaccount);
            abnormalList.add(balanceVo);
        }
    }

    private List<ExportTransferVo> handleDataBase(CashExportReq req) {
        // 查询基础数据
        List<SpCashBalanceClosingAsAt> spCashBalanceClosingAsAts = spCashBalanceClosingAsAtMapper.querySpCashBalanceClosingAsAt(req);

        // 过滤掉 clntCode 首字母为 C 的数据
        spCashBalanceClosingAsAts = spCashBalanceClosingAsAts.stream()
                .filter(closingAsAt -> closingAsAt.getClntCode() == null ||
                        !closingAsAt.getClntCode().startsWith("C"))
                .peek(e -> {
                    if (e.getClntCode() != null) {
                        e.setClntCode(e.getClntCode().trim()); // 先 trim 再设置
                    }
                })
                .collect(Collectors.toList());

        List<CmsView> cmsViews = fetchCmsViewsInParallel(spCashBalanceClosingAsAts);
        Map<String, String> acctMap = cmsViews.stream()
                .collect(Collectors.toMap(CmsView::getAccountcode, CmsView::getDefaulttradingacc, (a, b) -> a));

        // 设置账户信息
        for (SpCashBalanceClosingAsAt closingAsAt : spCashBalanceClosingAsAts) {
            String account = acctMap.getOrDefault(closingAsAt.getClntCode(), ""); // 防止空指针
            closingAsAt.setAccounts(account);
        }

        // 分组数据
        Map<String, Map<String, Map<String, List<SpCashBalanceClosingAsAt>>>> groupedData =
                spCashBalanceClosingAsAts.stream()
                        .collect(Collectors.groupingBy(
                                SpCashBalanceClosingAsAt::getClntCode,
                                Collectors.groupingBy(
                                        SpCashBalanceClosingAsAt::getAccounts,
                                        Collectors.groupingBy(
                                                SpCashBalanceClosingAsAt::getCcy
                                        )
                                )
                        ));

        // 处理分组结果
        List<ExportTransferVo> balanceVos = new ArrayList<>();
        for (Map.Entry<String, Map<String, Map<String, List<SpCashBalanceClosingAsAt>>>> clientEntry : groupedData.entrySet()) {
            if (clientEntry.getValue().size() > 0) {
                for (Map.Entry<String, Map<String, List<SpCashBalanceClosingAsAt>>> accountEntry : clientEntry.getValue().entrySet()) {
                    if (accountEntry.getValue().size() > 0) {
                        for (Map.Entry<String, List<SpCashBalanceClosingAsAt>> currencyEntry : accountEntry.getValue().entrySet()) {
                            if (currencyEntry.getValue().size() > 0) {
                                SpCashBalanceClosingAsAt firstRecord = currencyEntry.getValue().get(0); // 安全获取第一条记录
                                if (firstRecord != null) {
                                    ExportTransferVo vo = new ExportTransferVo();
                                    vo.setClntCode(firstRecord.getClntCode());
                                    vo.setAccounts(firstRecord.getAccounts());
                                    vo.setCcy(firstRecord.getCcy());
                                    vo.setBalance(currencyEntry.getValue().stream()
                                            .map(SpCashBalanceClosingAsAt::getAsAt)
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
        for (ExportTransferVo balanceVo : balanceVos) {
            balanceVo.setAccounts(AcctTypeEnum.getByCode(balanceVo.getAccounts()));
            balanceVo.setCcy(CcyTypeEnum.getByCodeMapping(balanceVo.getCcy()));
        }
        return balanceVos;
    }

    private List<CmsView> fetchCmsViewsInParallel(List<SpCashBalanceClosingAsAt> spCashBalanceClosingAsAts) {
        List<CmsView> cmsViews = new ArrayList<>();
        List<String> accountCodes = spCashBalanceClosingAsAts.stream()
                .map(SpCashBalanceClosingAsAt::getClntCode)
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

    /**
     * 获取vcbaccount表数据 多线程处理
     * @param balanceVos
     * @param clntCode 控制查询条件
     * @return
     */
    private List<Vcbaccount> fetchVcbaccountsInParallel(List<ExportTransferVo> balanceVos,String clntCode,BalancetypeEnum balanceType) {
        List<Vcbaccount> result = new ArrayList<>();

        int batchSize = 1000;
        int threadCount = Runtime.getRuntime().availableProcessors(); // 获取可用核心数作为线程数
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        List<Future<List<Vcbaccount>>> futures = new ArrayList<>();

        // 拆分 clientIds 并提交任务
        for (int i = 0; i < balanceVos.size(); i += batchSize) {
            int endIndex = Math.min(i + batchSize, balanceVos.size());
            List<ExportTransferVo> voList = balanceVos.subList(i, endIndex);
            // 提取参数并去重
            List<String> batchClientIds = voList.stream()
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

            List<String> currencyIds = voList.stream()
                    .map(ExportTransferVo::getCcy)
                    .filter(Objects::nonNull)
                    .distinct()
                    .collect(Collectors.toList());

            List<BigDecimal> balances = voList.stream()
                    .map(ExportTransferVo::getBalance)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

            Callable<List<Vcbaccount>> task = () -> {
                VcbaccountExample example = new VcbaccountExample();
                VcbaccountExample.Criteria criteria = example.createCriteria();
                if (clntCode != null) {
                    criteria.andClientidNotIn(batchClientIds);
                    criteria.andAccountseqNotIn(accountSeqs);
                    criteria.andCurrencyidNotIn(currencyIds);
                    if (balanceType.equals(BalancetypeEnum.L)) {
                        criteria.andLedgerbalNotIn(balances);
                    }else if (balanceType.equals(BalancetypeEnum.D)) {
                        criteria.andCmanualholdNotIn(balances);
                    }else if (balanceType.equals(BalancetypeEnum.O)) {
                        criteria.andCsettledNotIn(balances);
                    }
                }else {
                    criteria.andClientidIn(batchClientIds);
                    criteria.andAccountseqIn(accountSeqs);
                    criteria.andCurrencyidIn(currencyIds);
                }
                return vcbaccountMapper.selectByExample(example);
            };

            Future<List<Vcbaccount>> future = executorService.submit(task);
            futures.add(future);
        }

        // 收集所有线程的结果
        for (Future<List<Vcbaccount>> future : futures) {
            try {
                result.addAll(future.get()); // 等待任务完成并获取结果
            } catch (Exception e) {
                throw new RuntimeException("Failed to fetch Vcbaccount data", e);
            }
        }

        executorService.shutdown(); // 关闭线程池
        return result;
    }
}
