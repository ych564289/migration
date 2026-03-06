package com.example.migration.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.migration.dao.master.entity.InstrumentVersion;

import java.util.List;

public interface InstrumentVersionService extends IService<InstrumentVersion> {
    List<String> saveInstrumentBatch(List<InstrumentVersion> list);

    void deleteInstrument();
}
