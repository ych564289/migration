package com.example.migration.pojo.export.vo;

import com.alibaba.excel.annotation.ExcelProperty;
import com.example.migration.dao.slave.entity.Vcbaccount;
import com.example.migration.dao.slave.entity.Vcbtradingacc;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class ExportTransferVo {

    private String clntCode;

    private String accounts;

    private String ccy;

    private BigDecimal balance;

    private String reason;

    // ttl
    private String ttlClientid;

    private String ttlAccountseq;

    private String ttlCurrencyid;

    private BigDecimal ttlLedgerbal;

    private Vcbaccount vcbaccountInfo;

    // ---------  InstrumentBalance  ------
    private String marketId;

    private String instrument;

    private String clientid;

    private String instrumentid;

    private Integer tradingaccseq;

    private BigDecimal ledgerqty;

    private String ttlMarketId;

    private Vcbtradingacc vcbtradingaccInfo;


}
