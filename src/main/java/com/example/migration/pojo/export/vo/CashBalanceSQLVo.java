package com.example.migration.pojo.export.vo;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class CashBalanceSQLVo {

    private String clientid;

    private String ccy;

    private BigDecimal issueamt;

    private String rem;

}
