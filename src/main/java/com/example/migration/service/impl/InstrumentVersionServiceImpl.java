package com.example.migration.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.migration.dao.master.entity.InstrumentExtVersion;
import com.example.migration.dao.master.entity.InstrumentVersion;
import com.example.migration.dao.master.mapper.InstrumentVersionMapper;
import com.example.migration.service.InstrumentVersionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionCallbackWithoutResult;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class InstrumentVersionServiceImpl extends ServiceImpl<InstrumentVersionMapper, InstrumentVersion> implements InstrumentVersionService{

    @Autowired
    private InstrumentVersionMapper instrumentVersionMapper;

    @Autowired
    @Qualifier("masterTransactionTemplate")
    private TransactionTemplate transactionTemplate;


    @Autowired
    @Qualifier("masterTransactionManager")
    private PlatformTransactionManager masterTransactionManager;


    @Override
    //@Transactional(rollbackFor = Exception.class,value = "masterTransactionManager")
    public List<String> saveInstrumentBatch(List<InstrumentVersion> entityList) {
        if (entityList == null || entityList.isEmpty()) {
            return Collections.emptyList();
        }

        int batchSize = 25;
        // 将 Collection 转换为 List 以便进行子列表操作
        List<InstrumentVersion> list = new ArrayList<>(entityList);
        List<String> errorList = new ArrayList<>();
        for (int i = 0; i < list.size(); i += batchSize) {
            int end = Math.min(i + batchSize, list.size());
            List<InstrumentVersion> subList = list.subList(i, end);

            try {
                transactionTemplate.execute(new TransactionCallbackWithoutResult() {
                    @Override
                    protected void doInTransactionWithoutResult(TransactionStatus status) {
                        // 自动提交
                        instrumentVersionMapper.saveBatch(subList);
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
                // 记录日志后继续
                errorList.addAll(subList.stream().map(InstrumentVersion::getInstrument).distinct().collect(Collectors.toList()));
            }
        }
        return errorList;
    }
}
