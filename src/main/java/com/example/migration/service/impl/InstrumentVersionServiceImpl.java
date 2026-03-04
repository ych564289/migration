package com.example.migration.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.migration.dao.master.entity.InstrumentVersion;
import com.example.migration.dao.master.mapper.InstrumentVersionMapper;
import com.example.migration.service.InstrumentVersionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;

@Service
public class InstrumentVersionServiceImpl extends ServiceImpl<InstrumentVersionMapper, InstrumentVersion> implements InstrumentVersionService{

    @Autowired
    private InstrumentVersionMapper instrumentVersionMapper;

    @Override
    @Transactional(rollbackFor = Exception.class,value = "masterTransactionManager")
    public boolean saveBatch(Collection<InstrumentVersion> entityList) {
        return instrumentVersionMapper.saveBatch(entityList);
    }
}
