package com.example.migration.service;

import com.example.migration.dao.master.entity.SpCashBalanceClosingAsAt;
import com.example.migration.pojo.export.req.CashExportReq;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.List;

public interface ExportDifferentialDataService {

    void cashExport(CashExportReq req, HttpServletResponse response) throws IOException;

}
