package com.example.migration.service;

import com.example.migration.pojo.logMigration.req.MigrationReq;

public interface LogRecordMigrationService {

    String migration(MigrationReq req);

}
