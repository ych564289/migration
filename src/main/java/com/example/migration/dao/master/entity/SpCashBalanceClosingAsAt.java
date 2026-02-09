package com.example.migration.dao.master.entity;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class SpCashBalanceClosingAsAt {

    private String clntCode;

    private String acctType;

    private String market;

    private String ccy;

    private String balanceType;

    private BigDecimal asAt;

}
