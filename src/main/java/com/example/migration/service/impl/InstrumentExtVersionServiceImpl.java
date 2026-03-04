package com.example.migration.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.migration.dao.master.entity.InstrumentExtVersion;
import com.example.migration.dao.master.mapper.InstrumentExtVersionMapper;
import com.example.migration.service.InstrumentExtVersionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Collection;

@Service
public class InstrumentExtVersionServiceImpl extends ServiceImpl<InstrumentExtVersionMapper, InstrumentExtVersion> implements InstrumentExtVersionService {

    @Autowired
    private InstrumentExtVersionMapper instrumentExtVersionMapper;

    @Override
    public boolean saveBatch(Collection<InstrumentExtVersion> entityList) {
        return instrumentExtVersionMapper.saveBatch(entityList);
    }
}
