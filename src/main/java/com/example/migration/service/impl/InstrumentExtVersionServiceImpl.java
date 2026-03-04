package com.example.migration.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.migration.dao.master.entity.InstrumentExtVersion;
import com.example.migration.dao.master.mapper.InstrumentExtVersionMapper;
import com.example.migration.service.InstrumentExtVersionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Service
public class InstrumentExtVersionServiceImpl extends ServiceImpl<InstrumentExtVersionMapper, InstrumentExtVersion> implements InstrumentExtVersionService {

    @Autowired
    private InstrumentExtVersionMapper instrumentExtVersionMapper;

    @Override
    @Transactional(rollbackFor = Exception.class,value = "masterTransactionManager")
    public boolean saveBatch(Collection<InstrumentExtVersion> entityList) {
        if (entityList == null || entityList.isEmpty()) {
            return true;
        }

        int batchSize = 1000;
        // 将 Collection 转换为 List 以便进行子列表操作
        List<InstrumentExtVersion> list = new ArrayList<>(entityList);

        for (int i = 0; i < list.size(); i += batchSize) {
            int end = Math.min(i + batchSize, list.size());
            List<InstrumentExtVersion> subList = list.subList(i, end);

            boolean success = instrumentExtVersionMapper.saveBatch(subList);
            if (!success) {
                return false;
            }
        }
        return true;
    }
}
