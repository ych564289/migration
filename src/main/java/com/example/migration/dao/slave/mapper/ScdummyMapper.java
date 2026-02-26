package com.example.migration.dao.slave.mapper;

import com.example.migration.pojo.export.vo.CashBalanceSQLVo;
import com.example.migration.pojo.export.vo.InstrumentSQLVo;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface ScdummyMapper {

    List<InstrumentSQLVo> queryInstrumentSqlList(@Param("sql") String  sql);

    List<CashBalanceSQLVo> queryCashBalanceSqlList(@Param("sql") String  sql);


}
