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
        VcbaccountExample example = new VcbaccountExample();
        example.createCriteria()
                .andClientidNotIn(balanceVos.stream()
                        .map(SpCashBalanceVo::getAccounts)
                        .filter(Objects::nonNull) // 过滤空值
                        .collect(Collectors.toList()))
                .andAccountseqNotIn(balanceVos.stream()
                        .map(SpCashBalanceVo::getAccounts)
                        .filter(Objects::nonNull) // 过滤空值
                        .map(Integer::valueOf) // 转换为 Integer 类型
                        .distinct()
                        .collect(Collectors.toList()))
                .andCurrencyidNotIn(balanceVos.stream()
                        .map(SpCashBalanceVo::getCcy)
                        .filter(Objects::nonNull)
                        .distinct()
                        .collect(Collectors.toList()))
                .andLedgerbalNotIn(balanceVos.stream()
                        .map(SpCashBalanceVo::getBalance)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList()));
        List<Vcbaccount> vcbaccounts = vcbaccountMapper.selectByExample(example);
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

        VcbaccountExample example = new VcbaccountExample();
        example.createCriteria()
                .andClientidIn(balanceVos.stream()
                        .map(SpCashBalanceVo::getAccounts)
                        .filter(Objects::nonNull) // 过滤空值
                        .collect(Collectors.toList()))
                .andAccountseqIn(balanceVos.stream()
                        .map(SpCashBalanceVo::getAccounts)
                        .filter(Objects::nonNull) // 过滤空值
                        .map(Integer::valueOf) // 转换为 Integer 类型
                        .distinct()
                        .collect(Collectors.toList()))
                .andCurrencyidIn(balanceVos.stream()
                        .map(SpCashBalanceVo::getCcy)
                        .filter(Objects::nonNull)
                        .distinct()
                        .collect(Collectors.toList()));
        List<Vcbaccount> vcbaccounts = vcbaccountMapper.selectByExample(example);
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
        CmsViewExample example = new CmsViewExample();
        example.createCriteria()
                .andAccountcodeIn(spCashBalanceClosingAsAts.stream()
                        .map(SpCashBalanceClosingAsAt::getClntCode)
                        .filter(Objects::nonNull) // 过滤空值
                        .collect(Collectors.toList()));
        List<CmsView> cmsViews = cmsViewMapper.selectByExample(example);
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
            if (clientEntry.getValue().size() > 1) {
                for (Map.Entry<String, Map<String, List<SpCashBalanceClosingAsAt>>> accountEntry : clientEntry.getValue().entrySet()) {
                    if (accountEntry.getValue().size() > 1) {
                        for (Map.Entry<String, List<SpCashBalanceClosingAsAt>> currencyEntry : accountEntry.getValue().entrySet()) {
                            if (currencyEntry.getValue().size() > 1) {
                                SpCashBalanceVo vo = new SpCashBalanceVo();
                                SpCashBalanceClosingAsAt firstRecord = currencyEntry.getValue().get(0); // 安全获取第一条记录
                                if (firstRecord != null) {
                                    vo.setAccounts(firstRecord.getAccounts());
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
}
