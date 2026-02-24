package com.example.migration.dao.master.mapper;

import com.example.migration.dao.master.entity.SpCashBalanceClosingAsAt;
import com.example.migration.dao.master.entity.SpInstrumentBalanceClosingAsAt;
import com.example.migration.pojo.export.req.CashExportReq;

import java.util.List;

public interface SpCashBalanceClosingAsAtMapper {

    List<SpCashBalanceClosingAsAt> querySpCashBalanceClosingAsAt(CashExportReq req);

    List<SpInstrumentBalanceClosingAsAt> querySpInstrumentBalanceClosingAsAt(CashExportReq req);

}
