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

    @ExcelProperty("reason")
    private String reason;

}
