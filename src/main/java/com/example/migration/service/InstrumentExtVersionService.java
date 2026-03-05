package com.example.migration.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.migration.dao.master.entity.InstrumentExtVersion;
import com.example.migration.dao.master.entity.InstrumentVersion;

import java.util.List;

public interface InstrumentExtVersionService extends IService<InstrumentExtVersion> {

    List<String> saveInstrumentExtBatch(List<InstrumentExtVersion> instrumentExtVersions);

}
