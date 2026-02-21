package com.example.migration.designPattern.exportDifferential;

import com.example.migration.dao.master.entity.CmsView;
import com.example.migration.dao.master.entity.CmsViewExample;
import com.example.migration.dao.master.entity.SpCashBalanceClosingAsAt;
import com.example.migration.dao.master.mapper.CmsViewMapper;
import com.example.migration.dao.master.mapper.SpCashBalanceClosingAsAtMapper;
import com.example.migration.dao.slave.entity.Vcbaccount;
import com.example.migration.dao.slave.entity.VcbaccountExample;
import com.example.migration.dao.slave.mapper.VcbaccountMapper;
import com.example.migration.designPattern.ExportDifferentialStrategy;
import com.example.migration.enumeration.export.AcctTypeEnum;
import com.example.migration.enumeration.export.BalancetypeEnum;
import com.example.migration.enumeration.export.CcyTypeEnum;
import com.example.migration.enumeration.export.DataSourceTypeEnum;
import com.example.migration.pojo.export.req.CashExportReq;
import com.example.migration.pojo.export.vo.SpCashBalanceVo;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@Component
public class SettledDataHandle implements ExportDifferentialStrategy {

    @Autowired
    private SpCashBalanceClosingAsAtMapper spCashBalanceClosingAsAtMapper;

    @Autowired
    private CmsViewMapper cmsViewMapper;

    @Autowired
    private VcbaccountMapper vcbaccountMapper;

    @Override
    public DataSourceTypeEnum getDataSourceType() {
        return DataSourceTypeEnum.Cash;
    }

    @Override
    public Map<String, List<SpCashBalanceVo>> queryExportDifferentialData(CashExportReq req) {
        Map<String, List<SpCashBalanceVo>> map = new HashMap<>();
        // 查询基础数据
        List<SpCashBalanceVo> balanceVos = handleDataBase(req);
        List<SpCashBalanceVo> sameList = new ArrayList<>();
        List<SpCashBalanceVo> abnormalList = new ArrayList<>();
        List<SpCashBalanceVo> exclusiveList = new ArrayList<>();
        octobackDataCollectionProcess(balanceVos, sameList, abnormalList, exclusiveList);

        // todo 数据不一致 需查询具体原因
        //ttl特有的
        List<SpCashBalanceVo> ttlList = ttlDataCollectionProcess(balanceVos);
        map.put("sameList", sameList);  //数据一致
        map.put("abnormalList", abnormalList);  //数据异常
        map.put("exclusiveList", exclusiveList);   //存储过程特有
        map.put("ttlList", ttlList);   //ttl特有
        return map;
    }

    /**
     * ttl数据处理
     **/
    private List<SpCashBalanceVo> ttlDataCollectionProcess(List<SpCashBalanceVo> balanceVos) {
        List<SpCashBalanceVo> ttlList = new ArrayList<>();
        List<BigDecimal> balanceList = balanceVos.stream()
                .map(SpCashBalanceVo::getBalance)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        List<Vcbaccount> vcbaccounts = fetchVcbaccountsInParallel(balanceVos,"1");
        vcbaccounts = vcbaccounts.stream().filter(e -> balanceList.contains(e.getLedgerbal())).collect(Collectors.toList());
        for (Vcbaccount vcbaccount : vcbaccounts) {
            SpCashBalanceVo vo = new SpCashBalanceVo();
            vo.setClntCode(vcbaccount.getClientid());
            vo.setAccounts(String.valueOf(vcbaccount.getAccountseq()));
            vo.setCcy(vcbaccount.getCurrencyid());
            vo.setBalance(vcbaccount.getLedgerbal());
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
    private void octobackDataCollectionProcess(List<SpCashBalanceVo> balanceVos,
                                                     List<SpCashBalanceVo> sameList,
                                                     List<SpCashBalanceVo> abnormalList,
                                                     List<SpCashBalanceVo> exclusiveList) {

        List<Vcbaccount> vcbaccounts = fetchVcbaccountsInParallel(balanceVos,null);
        Map<String, BigDecimal> decimalMap = vcbaccounts.stream()
                .collect(Collectors.toMap(e -> e.getClientid() + e.getAccountseq() + e.getCurrencyid(), Vcbaccount::getLedgerbal, (a, b) -> a));
        for (SpCashBalanceVo balanceVo : balanceVos) {
            String key = balanceVo.getClntCode() + balanceVo.getAccounts() + balanceVo.getCcy();
            if (decimalMap.containsKey(key)) {
                BigDecimal ledgerBal = decimalMap.get(key);
                if (ledgerBal.compareTo(balanceVo.getBalance()) == 0) {
                    balanceVo.setReason("Match");
                    sameList.add(balanceVo);
                } else {
                    abnormalList.add(balanceVo);
                }
            } else {
                exclusiveList.add(balanceVo);
            }
        }
    }

    private List<SpCashBalanceVo> handleDataBase(CashExportReq req) {
        // 查询基础数据
        List<SpCashBalanceClosingAsAt> spCashBalanceClosingAsAts = spCashBalanceClosingAsAtMapper.querySpCashBalanceClosingAsAt(req);

        // 过滤掉 clntCode 首字母为 C 的数据
        spCashBalanceClosingAsAts = spCashBalanceClosingAsAts.stream()
                .filter(closingAsAt -> closingAsAt.getClntCode() == null ||
                        !closingAsAt.getClntCode().startsWith("C"))
                .collect(Collectors.toList());

        // 构建账户映射关系
//        CmsViewExample example = new CmsViewExample();
//        example.createCriteria()
//                .andAccountcodeIn(spCashBalanceClosingAsAts.stream()
//                        .map(SpCashBalanceClosingAsAt::getClntCode)
//                        .filter(Objects::nonNull) // 过滤空值
//                        .collect(Collectors.toList()));
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
        List<SpCashBalanceVo> balanceVos = new ArrayList<>();
        for (Map.Entry<String, Map<String, Map<String, List<SpCashBalanceClosingAsAt>>>> clientEntry : groupedData.entrySet()) {
            if (clientEntry.getValue().size() > 0) {
                for (Map.Entry<String, Map<String, List<SpCashBalanceClosingAsAt>>> accountEntry : clientEntry.getValue().entrySet()) {
                    if (accountEntry.getValue().size() > 0) {
                        for (Map.Entry<String, List<SpCashBalanceClosingAsAt>> currencyEntry : accountEntry.getValue().entrySet()) {
                            if (currencyEntry.getValue().size() > 0) {
                                SpCashBalanceVo vo = new SpCashBalanceVo();
                                SpCashBalanceClosingAsAt firstRecord = currencyEntry.getValue().get(0); // 安全获取第一条记录
                                if (firstRecord != null) {
                                    vo.setClntCode(firstRecord.getClntCode());
                                    vo.setAccounts(firstRecord.getAcctType());
                                    vo.setCcy(firstRecord.getCcy());
                                    vo.setBalance(currencyEntry.getValue().stream()
                                            .map(SpCashBalanceClosingAsAt::getAsAt)
                                            .filter(Objects::nonNull) // 过滤空值
                                            .reduce(BigDecimal.ZERO, BigDecimal::add));
                                    balanceVos.add(vo);
                                }
                            }
                        }
                    }
                }
            }
        }
        for (SpCashBalanceVo balanceVo : balanceVos) {
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
    private List<Vcbaccount> fetchVcbaccountsInParallel(List<SpCashBalanceVo> balanceVos,String clntCode) {
        List<Vcbaccount> result = new ArrayList<>();

        // 提取参数并去重
        List<String> clientIds = balanceVos.stream()
                .map(SpCashBalanceVo::getClntCode)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());

        List<Integer> accountSeqs = balanceVos.stream()
                .map(SpCashBalanceVo::getAccounts)
                .filter(Objects::nonNull)
                .map(Integer::valueOf)
                .distinct()
                .collect(Collectors.toList());

        List<String> currencyIds = balanceVos.stream()
                .map(SpCashBalanceVo::getCcy)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());

        int batchSize = 1000;
        int threadCount = Runtime.getRuntime().availableProcessors(); // 获取可用核心数作为线程数
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        List<Future<List<Vcbaccount>>> futures = new ArrayList<>();

        // 拆分 clientIds 并提交任务
        for (int i = 0; i < clientIds.size(); i += batchSize) {
            int endIndex = Math.min(i + batchSize, clientIds.size());
            List<String> batchClientIds = clientIds.subList(i, endIndex);

            Callable<List<Vcbaccount>> task = () -> {
                VcbaccountExample example = new VcbaccountExample();
                VcbaccountExample.Criteria criteria = example.createCriteria();
                if (clntCode != null) {
                    criteria.andClientidNotIn(batchClientIds);
                    criteria.andAccountseqNotIn(accountSeqs);
                    criteria.andCurrencyidNotIn(currencyIds);
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
