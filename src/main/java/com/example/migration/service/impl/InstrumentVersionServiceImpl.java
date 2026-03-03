package com.example.migration.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.migration.dao.master.entity.InstrumentVersion;
import com.example.migration.dao.master.mapper.InstrumentVersionMapper;
import com.example.migration.service.InstrumentVersionService;
import org.springframework.stereotype.Service;

@Service
public class InstrumentVersionServiceImpl extends ServiceImpl<InstrumentVersionMapper, InstrumentVersion> implements InstrumentVersionService{
}
