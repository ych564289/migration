package com.example.migration.enumeration.export;

public enum BalancetypeEnum {

    O("O","Settled"),
    L("L","Ledger"),
    D("D","Hold Dr"),
    ;

    private String code;

    private String desc;

    BalancetypeEnum(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }
    public String getCode() {
        return code;
    }
    public String getDesc() {
        return desc;
    }

}
