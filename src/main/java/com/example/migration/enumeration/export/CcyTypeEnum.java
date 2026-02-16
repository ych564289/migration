package com.example.migration.enumeration.export;

public enum CcyTypeEnum {

    RMB("RMB", "CNY", "人民币"),

    YEN("YEN", "JPY", "日元")
    ;

    private String code;

    private String codeMapping;

    private String desc;

    private CcyTypeEnum(String code, String codeMapping, String desc) {
        this.code = code;
        this.codeMapping = codeMapping;
        this.desc = desc;
    }

    public String getCode() {
        return code;
    }
    public String getCodeMapping() {
        return codeMapping;
    }
    public String getDesc() {
        return desc;
    }

    public static String getByCodeMapping(String code) {
        for (CcyTypeEnum value : CcyTypeEnum.values()) {
            if (value.getCode().equals(code)) {
                return value.getCodeMapping();
            }
        }
        return code;
    }

}
