package com.example.migration.pojo.export.vo;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class SpInstrumentVo {

    @ExcelProperty("clntCode")
    private String clntCode;

    @ExcelProperty("accounts")
    private String accounts;

    @ExcelProperty("instrument")
    private String instrument;

    @ExcelProperty("marketId")
    private String marketId;

    @ExcelProperty("balance")
    private BigDecimal balance;

    // --------------

    @ExcelProperty("ttl-clntCode")
    private String clientid;

    @ExcelProperty("ttl-accounts")
    private Integer tradingaccseq;

    @ExcelProperty("ttl-instrument")
    private String instrumentid;

    @ExcelProperty("ttl-marketId")
    private String ttlMarketId;

    @ExcelProperty("ttl-balance")
    private BigDecimal ledgerqty;

    @ExcelProperty("reason")
    private String reason;

}
