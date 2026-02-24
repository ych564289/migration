package com.example.migration.dao.master.mapper;

import com.example.migration.dao.master.entity.SpInstrumentBalanceClosingAsAt;
import com.example.migration.pojo.export.req.CashExportReq;

import java.util.List;

public interface SpInstrumentBalanceClosingAsAtMapper {

    List<SpInstrumentBalanceClosingAsAt> querySpInstrumentBalanceClosingAsAt(CashExportReq req);


}
