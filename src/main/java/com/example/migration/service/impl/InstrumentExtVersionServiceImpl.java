package com.example.migration.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.migration.dao.master.entity.InstrumentExtVersion;
import com.example.migration.dao.master.mapper.InstrumentExtVersionMapper;
import com.example.migration.service.InstrumentExtVersionService;
import org.springframework.stereotype.Service;

@Service
public class InstrumentExtVersionServiceImpl extends ServiceImpl<InstrumentExtVersionMapper, InstrumentExtVersion> implements InstrumentExtVersionService {
}
