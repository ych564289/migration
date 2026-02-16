package com.example.migration.controller;

import com.example.migration.dao.master.entity.SpCashBalanceClosingAsAt;
import com.example.migration.pojo.export.req.CashExportReq;
import com.example.migration.service.ExportDifferentialDataService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;

@RestController()
@RequestMapping("/exportDifferentialData")
public class ExportDifferentialDataController {

    @Autowired
    private ExportDifferentialDataService sysUserService;

    @PostMapping("/cash/export")
    public void cashExport(@RequestBody CashExportReq req, HttpServletResponse response) {
        try {
            sysUserService.cashExport(req,response);
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException("文件下载异常");
        }
    }

}
