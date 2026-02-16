package com.example.migration.designPattern.exportDifferential;

import com.example.migration.designPattern.ExportDifferentialStrategy;
import com.example.migration.enumeration.export.BalancetypeEnum;
import com.example.migration.pojo.export.req.CashExportReq;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
abstract class ExportDifferenceServiceLocator implements ApplicationContextAware {

    private Map<String, ExportDifferentialStrategy> exportDifferentialStrategyMap;
    private List<ExportDifferentialStrategy> exportDifferentialStrategyList;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        // 通过上下文，根据接口类型返回相应的所有实现类bean
        exportDifferentialStrategyMap = applicationContext.getBeansOfType(ExportDifferentialStrategy.class);
        exportDifferentialStrategyList = new ArrayList<>(exportDifferentialStrategyMap.values());
    }

    /**
     * 根据账户类型获取对应的导出 differential 数据实现类
     *
     * @param req
     * @return
     */
    public ExportDifferentialStrategy getExportDifferentialStrategy(CashExportReq req) {
        for (ExportDifferentialStrategy exportDifferentialStrategy : exportDifferentialStrategyList) {
            if (exportDifferentialStrategy.getDataSourceType().equals(req.getDataSourceTypeEnum())) {
                return exportDifferentialStrategy;
            }
        }
        return null;
    }

}
