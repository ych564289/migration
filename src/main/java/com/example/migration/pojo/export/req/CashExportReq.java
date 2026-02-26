package com.example.migration.pojo.export.req;

import com.example.migration.enumeration.export.BalancetypeEnum;
import com.example.migration.enumeration.export.DataSourceTypeEnum;
import lombok.Data;

import java.util.Date;

@Data
public class CashExportReq {

    /**
     * 导出时间
     */
    private String exportDate;

    /**
     * 数据源类型
     */
    private DataSourceTypeEnum dataSourceTypeEnum;

    /**
     * 账户类型
     */
    private BalancetypeEnum balanceType;

    /**
     * mq查询时间
     */
    private Date mqDate;

    private String sqlInfo;

}
