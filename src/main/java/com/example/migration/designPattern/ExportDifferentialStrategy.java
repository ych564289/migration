package com.example.migration.designPattern;

import com.example.migration.enumeration.export.BalancetypeEnum;
import com.example.migration.enumeration.export.DataSourceTypeEnum;
import com.example.migration.pojo.export.req.CashExportReq;
import com.example.migration.pojo.export.vo.SpCashBalanceVo;

import java.util.List;
import java.util.Map;

public interface ExportDifferentialStrategy {

    DataSourceTypeEnum getDataSourceType();

    Map<String, List<SpCashBalanceVo>>  queryExportDifferentialData(CashExportReq req);

}
