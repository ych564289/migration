package com.example.migration.dao.master.mapper;

import com.example.migration.dao.master.entity.SpInstrumentBalanceClosingAsAt;
import com.example.migration.pojo.export.req.CashExportReq;
import com.example.migration.pojo.export.vo.InstrumentSQLVo;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface SpInstrumentBalanceClosingAsAtMapper {

    List<SpInstrumentBalanceClosingAsAt> querySpInstrumentBalanceClosingAsAt(CashExportReq req);

    List<InstrumentSQLVo> querySqlList(@Param("sql") String  sql);

}
