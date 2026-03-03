package com.example.migration.controller;

import com.example.migration.pojo.export.req.CashExportReq;
import com.example.migration.pojo.logMigration.req.MigrationReq;
import com.example.migration.service.LogRecordMigrationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@RestController()
@RequestMapping("/exportDifferentialData")
public class LogRecordMigrationController {

    @Autowired
    private LogRecordMigrationService logRecordMigrationService;

    @PostMapping("/logRecordMigration")
    public String logRecordMigration(@RequestBody MigrationReq req) {
        try {
            return logRecordMigrationService.migration(req);
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("文件下载异常");
        }
    }

}
