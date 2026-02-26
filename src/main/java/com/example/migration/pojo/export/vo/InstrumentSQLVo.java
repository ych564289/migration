package com.example.migration.pojo.export.vo;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class InstrumentSQLVo {

    private String clientid;

    private String ttlmarketid;

    private String instrument;

    private BigDecimal issueamt;

    private String rem;


}
