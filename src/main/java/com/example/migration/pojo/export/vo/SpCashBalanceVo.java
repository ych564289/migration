package com.example.migration.pojo.export.vo;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class SpCashBalanceVo {

    @ExcelProperty("clntCode")
    private String clntCode;

    @ExcelProperty("accounts")
    private String accounts;

    @ExcelProperty("ccy")
    private String ccy;

    @ExcelProperty("balance")
    private BigDecimal balance;

    @ExcelProperty("ttl-clientid")
    private String ttlClientid;

    @ExcelProperty("ttl-accountseq")
    private String ttlAccountseq;

    @ExcelProperty("ttl-currencyid")
    private String ttlCurrencyid;

    @ExcelProperty("ttl-ledgerbal")
    private BigDecimal ttlLedgerbal;

    @ExcelProperty("reason")
    private String reason;



}
