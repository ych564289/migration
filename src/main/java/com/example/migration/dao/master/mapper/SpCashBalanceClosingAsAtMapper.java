package com.example.migration.dao.master.mapper;

import com.example.migration.dao.master.entity.SpCashBalanceClosingAsAt;
import com.example.migration.dao.master.entity.SpInstrumentBalanceClosingAsAt;
import com.example.migration.pojo.export.req.CashExportReq;
import com.example.migration.pojo.export.vo.CashBalanceSQLVo;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface SpCashBalanceClosingAsAtMapper {

    List<SpCashBalanceClosingAsAt> querySpCashBalanceClosingAsAt(CashExportReq req);
}
