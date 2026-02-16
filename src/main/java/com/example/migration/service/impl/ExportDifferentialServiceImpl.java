package com.example.migration.service.impl;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.ExcelWriter;
import com.alibaba.excel.write.metadata.WriteSheet;
import com.example.migration.dao.master.entity.SpCashBalanceClosingAsAt;
import com.example.migration.dao.master.mapper.SpCashBalanceClosingAsAtMapper;
import com.example.migration.designPattern.exportDifferential.ExportDifferencePattern;
import com.example.migration.pojo.export.req.CashExportReq;
import com.example.migration.pojo.export.vo.SpCashBalanceVo;
import com.example.migration.service.ExportDifferentialDataService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.List;
import java.util.Map;

@Service
public class ExportDifferentialServiceImpl implements ExportDifferentialDataService {

    @Autowired
    private ExportDifferencePattern exportDifferencePattern;
    @Override
    public void cashExport(CashExportReq req, HttpServletResponse response) throws IOException {
        Map<String, List<SpCashBalanceVo>> map = exportDifferencePattern.queryExportDifferentialData(req);
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding("utf-8");
        String name = req.getDataSourceTypeEnum().getCode() + "_" + req.getBalanceType().getDesc();
        String fileName = URLEncoder.encode(name, "UTF-8").replaceAll("\\+", "%20");
        response.setHeader("Content-disposition", "attachment;filename*=utf-8''" + fileName + ".xlsx");

        // 创建 ExcelWriter 对象
        ExcelWriter excelWriter = EasyExcel.write(response.getOutputStream()).build();

        // 定义第一个 Sheet
        WriteSheet writeSheet1 = EasyExcel.writerSheet(0, "数据一致")
                .head(SpCashBalanceVo.class) // 表头类
                .build();
        excelWriter.write(map.get("sameList"), writeSheet1);

        // 定义第二个 Sheet
        WriteSheet writeSheet2 = EasyExcel.writerSheet(1, "数据异常")
                .head(SpCashBalanceVo.class) // 表头类
                .build();
        excelWriter.write(map.get("abnormalList"), writeSheet2);

        // 定义第三个 Sheet
        WriteSheet writeSheet3 = EasyExcel.writerSheet(2, "存储过程特有")
                .head(SpCashBalanceVo.class) // 表头类
                .build();
        excelWriter.write(map.get("abnormalList"), writeSheet3);

        // 定义第四个 Sheet
        WriteSheet writeSheet4 = EasyExcel.writerSheet(3, "ttl特有")
                .head(SpCashBalanceVo.class) // 表头类
                .build();
        excelWriter.write(map.get("abnormalList"), writeSheet4);

        // 关闭写入器，确保数据写入完成
        excelWriter.finish();
    }
}
