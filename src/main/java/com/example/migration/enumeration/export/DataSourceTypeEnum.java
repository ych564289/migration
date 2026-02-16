package com.example.migration.enumeration.export;

public enum DataSourceTypeEnum {
    Cash("Cash"),
    Instrument("Instrument")
    ;

    private String code;


    DataSourceTypeEnum(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
