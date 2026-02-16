package com.example.migration.designPattern.exportDifferential;

import com.example.migration.designPattern.ExportDifferentialStrategy;
import com.example.migration.pojo.export.req.CashExportReq;
import com.example.migration.pojo.export.vo.SpCashBalanceVo;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class ExportDifferencePattern extends ExportDifferenceServiceLocator {

    public Map<String, List<SpCashBalanceVo>> queryExportDifferentialData(CashExportReq req) {
        ExportDifferentialStrategy exportDifferentialStrategy = getExportDifferentialStrategy(req);
        if (exportDifferentialStrategy == null) {
            throw new RuntimeException("未找到对应的导出 differential 策略");
        }
        return exportDifferentialStrategy.queryExportDifferentialData(req);
    }

}
