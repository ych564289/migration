package com.example.migration.enumeration.export;

public enum AcctTypeEnum {

    CASH( "CASH","1"),
    MRGN( "MRGN","2"),
    DVP( "DVP","3"),
    SBL( "SBL","4"),
    SBLLender( "SBLLender","5"),

    ;

    private String code;

    private String desc;

    private AcctTypeEnum(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public String getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }

    public static String getByCode(String code) {
        for (AcctTypeEnum acctTypeEunm : AcctTypeEnum.values()) {
            if (acctTypeEunm.getCode().equals(code)) {
                return acctTypeEunm.getDesc();
            }
        }
        return null;
    }

}
