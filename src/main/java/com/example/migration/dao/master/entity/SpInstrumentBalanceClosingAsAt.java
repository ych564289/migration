package com.example.migration.dao.master.entity;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class SpInstrumentBalanceClosingAsAt {

    private String clntCode;

    private String acctType;

    private String market;

    private String tradingCcy;

    private String instrument;

    private String balanceType;

    private BigDecimal asAt;

    private String accounts;

    private String ttlMarketID;

}
