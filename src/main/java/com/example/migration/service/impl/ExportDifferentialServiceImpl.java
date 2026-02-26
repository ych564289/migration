package com.example.migration.service.impl;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.ExcelWriter;
import com.alibaba.excel.write.metadata.WriteSheet;
import com.example.migration.dao.master.entity.SpCashBalanceClosingAsAt;
import com.example.migration.dao.master.mapper.SpCashBalanceClosingAsAtMapper;
import com.example.migration.designPattern.exportDifferential.ExportDifferencePattern;
import com.example.migration.enumeration.export.DataSourceTypeEnum;
import com.example.migration.pojo.export.req.CashExportReq;
import com.example.migration.pojo.export.vo.ExportTransferVo;
import com.example.migration.pojo.export.vo.SpCashBalanceVo;
import com.example.migration.pojo.export.vo.SpInstrumentVo;
import com.example.migration.service.ExportDifferentialDataService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Service
public class ExportDifferentialServiceImpl implements ExportDifferentialDataService {

    @Autowired
    private ExportDifferencePattern exportDifferencePattern;

    @Override
    public void cashExport(CashExportReq req, HttpServletResponse response) throws IOException {
        Map<String, List<ExportTransferVo>> map = exportDifferencePattern.queryExportDifferentialData(req);
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding("utf-8");
        String name = req.getDataSourceTypeEnum().getCode() + "_" + req.getBalanceType().getDesc();
        String fileName = URLEncoder.encode(name, "UTF-8").replaceAll("\\+", "%20");
        response.setHeader("Content-disposition", "attachment;filename*=utf-8''" + fileName + ".xlsx");

        // 创建 ExcelWriter 对象
        ExcelWriter excelWriter = EasyExcel.write(response.getOutputStream()).build();

        if (req.getDataSourceTypeEnum().equals(DataSourceTypeEnum.Cash)) {
            processAndWriteSheet(excelWriter, map, req, SpCashBalanceVo.class, SpCashBalanceVo::new);
        } else if (req.getDataSourceTypeEnum().equals(DataSourceTypeEnum.Instrument)) {
            processAndWriteSheet(excelWriter, map, req, SpInstrumentVo.class, SpInstrumentVo::new);
        }

        // 关闭写入器，确保数据写入完成
        excelWriter.finish();
    }

    /**
     * 通用处理并写入 Excel Sheet 的方法
     *
     * @param excelWriter   Excel 写入器
     * @param map           数据源
     * @param req           请求参数
     * @param clazz         表头类
     * @param constructor   对象构造函数
     * @param <T>           泛型类型
     */
    private <T> void processAndWriteSheet(ExcelWriter excelWriter,
                                          Map<String, List<ExportTransferVo>> map,
                                          CashExportReq req,
                                          Class<T> clazz,
                                          Supplier<T> constructor) {
        WriteSheet writeSheet = EasyExcel.writerSheet(0, req.getExportDate() + "-" + req.getBalanceType().getDesc())
                .head(clazz)
                .build();

        List<T> abnormalList = convertToList(map.get("abnormalList"), constructor);
        List<T> sameList = convertToList(map.get("sameList"), constructor);
        List<T> exclusiveList = convertToList(map.get("exclusiveList"), constructor);
        List<T> ttlList = convertToList(map.get("ttlList"), constructor);

        abnormalList.addAll(exclusiveList);
        abnormalList.addAll(ttlList);
        abnormalList.addAll(sameList);

        excelWriter.write(abnormalList, writeSheet);
    }

    /**
     * 将 List<ExportTransferVo> 转换为指定类型的 List
     *
     * @param sourceList    源数据列表
     * @param constructor   目标对象构造函数
     * @param <T>           泛型类型
     * @return              转换后的列表
     */
    private <T> List<T> convertToList(List<ExportTransferVo> sourceList, Supplier<T> constructor) {
        return sourceList.stream()
                .map(e -> {
                    T target = constructor.get();
                    BeanUtils.copyProperties(e, target);
                    return target;
                })
                .collect(Collectors.toList());
    }

}
