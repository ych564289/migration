package com.example.migration.service.impl;

import cn.hutool.core.collection.CollectionUtil;
import com.example.migration.dao.master.entity.GenericLog;
import com.example.migration.dao.master.entity.InstrumentExtVersion;
import com.example.migration.dao.master.entity.InstrumentVersion;
import com.example.migration.dao.master.mapper.GenericLogMapper;
import com.example.migration.pojo.logMigration.req.MigrationReq;
import com.example.migration.service.InstrumentExtVersionService;
import com.example.migration.service.InstrumentVersionService;
import com.example.migration.service.LogRecordMigrationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Service
public class LogRecordMigrationServiceImpl implements LogRecordMigrationService {

    @Autowired
    private InstrumentVersionService instrumentVersionService;

    @Autowired
    private InstrumentExtVersionService instrumentExtVersionService;

    @Autowired
    private GenericLogMapper genericLogMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String migration(MigrationReq req) {
        String tableName = req.getTableName();
        Date currentDate = new Date();

        // 获取对应的迁移上下文配置
        MigrationContext context = getMigrationContext(tableName);
        if (context == null) {
            throw new IllegalArgumentException("Unsupported table name: " + tableName);
        }

        // 处理插入数据
        List<GenericLog> insertList = context.insertQuery.get();
        if (CollectionUtil.isNotEmpty(insertList)) {
            processMigration(insertList, currentDate, context, true);
        }

        // 处理更新数据
        List<GenericLog> updateList = context.updateQuery.get();
        if (CollectionUtil.isNotEmpty(updateList)) {
            processMigration(updateList, currentDate, context, false);
        }

        return "success";
    }

    /**
     * 通用迁移处理逻辑
     *
     * @param logList      日志列表
     * @param currentDate  当前时间
     * @param context      迁移上下文
     * @param isInsert     是否为插入操作
     */
    @SuppressWarnings("unchecked")
    private <T> void processMigration(List<GenericLog> logList, Date currentDate, MigrationContext context, boolean isInsert) {
        // 按 key 和时间分组
        Map<String, Map<Date, List<GenericLog>>> groupedMap = logList.stream()
                .collect(Collectors.groupingBy(
                        GenericLog::getTablekey1,
                        Collectors.groupingBy(GenericLog::getLogdatetime)
                ));

        List<T> resultList = new ArrayList<>();
        // 用于 Update 场景的基础数据列表（即本次运行中已处理的 Insert 或之前的 Update 结果）
        List<T> baseList = isInsert ? new ArrayList<>() : (List<T>) context.currentResultList;

        for (Map.Entry<String, Map<Date, List<GenericLog>>> mapEntry : groupedMap.entrySet()) {
            for (Map.Entry<Date, List<GenericLog>> entry : mapEntry.getValue().entrySet()) {
                List<GenericLog> groupList = entry.getValue();
                if (groupList.isEmpty()) continue;

                // 构建字段映射
                Map<String, String> fieldMap = groupList.stream()
                        .collect(Collectors.toMap(GenericLog::getLogcolumn, GenericLog::getAftervalue));

                GenericLog log = groupList.get(0);
                T entity;

                if (isInsert) {
                    entity = (T) context.creator.get();
                } else {
                    // Update 场景：尝试获取已有对象进行增量更新，否则新建
                    entity = (T) context.initializer.apply(log.getTablekey1(), resultList, baseList);
                    if (entity == null) {
                        entity = (T) context.creator.get();
                    }
                }

                // 设置公共日志字段
                setCommonLogFields(entity, log, currentDate);

                try {
                    populateInstrumentVersion(entity, fieldMap);
                    resultList.add(entity);
                } catch (Exception e) {
                    throw new RuntimeException("Failed to populate entity for key: " + log.getTablekey1(), e);
                }
            }
        }

        // 保存结果
        if (CollectionUtil.isNotEmpty(resultList)) {
            //context.saver.accept(resultList);
            // 如果是 Insert 阶段，将结果存入上下文供 Update 阶段使用
            if (isInsert) {
                context.currentResultList = resultList;
            }
        }
    }

    /**
     * 设置公共日志字段 (利用泛型擦除和 instanceof 处理多态)
     */
    @SuppressWarnings("unchecked")
    private <T> void setCommonLogFields(T entity, GenericLog log, Date date) {
        if (entity instanceof InstrumentVersion) {
            InstrumentVersion iv = (InstrumentVersion) entity;
            iv.setDate(date);
            iv.setLogdatetime(log.getLogdatetime());
            iv.setLoghostname(log.getLoghostname());
            iv.setLogusername(log.getLogusername());
        } else if (entity instanceof InstrumentExtVersion) {
            InstrumentExtVersion iev = (InstrumentExtVersion) entity;
            iev.setDate(date);
            iev.setLogdatetime(log.getLogdatetime());
            iev.setLoghostname(log.getLoghostname());
            iev.setLogusername(log.getLogusername());
        }
    }

    /**
     * 获取迁移上下文配置
     */
    private MigrationContext getMigrationContext(String tableName) {
        if ("Instrument".equals(tableName)) {
            return new MigrationContext(
                    () -> genericLogMapper.queryInstrumentVersionInsert(),
                    () -> genericLogMapper.queryInstrumentVersionUpdate(),
                    InstrumentVersion::new,
                    (tablekey1, resultList, baseList) -> initInstrumentVersionHandle(tablekey1, (List<InstrumentVersion>) resultList, (List<InstrumentVersion>) baseList),
                    (list) -> instrumentVersionService.saveBatch((List<InstrumentVersion>) list)
            );
        } else if ("InstrumentExt".equals(tableName)) {
            return new MigrationContext(
                    () -> genericLogMapper.queryInstrumentExtVersionInsert(),
                    () -> genericLogMapper.queryInstrumentExtVersionUpdate(),
                    InstrumentExtVersion::new,
                    (tablekey1, resultList, baseList) -> initInstrumentExtVersionHandle(tablekey1, (List<InstrumentExtVersion>) resultList, (List<InstrumentExtVersion>) baseList),
                    (list) -> instrumentExtVersionService.saveBatch((List<InstrumentExtVersion>) list)
            );
        }
        return null;
    }

    // --- 原有的初始化逻辑保持不变，仅调整泛型签名以适配函数式接口 ---

    private InstrumentVersion initInstrumentVersionHandle(String tablekey1, List<InstrumentVersion> currentList, List<InstrumentVersion> baseList) {
        if (CollectionUtil.isNotEmpty(currentList)) {
            return currentList.stream()
                    .filter(i -> tablekey1.equals(i.getInstrument()))
                    .max(Comparator.comparing(InstrumentVersion::getLogdatetime, Comparator.nullsLast(Comparator.naturalOrder())))
                    .orElse(null);
        }
        if (CollectionUtil.isNotEmpty(baseList)) {
            return baseList.stream()
                    .filter(i -> tablekey1.equals(i.getInstrument()))
                    .findFirst()
                    .orElse(null);
        }
        return null;
    }

    private InstrumentExtVersion initInstrumentExtVersionHandle(String tablekey1, List<InstrumentExtVersion> currentList, List<InstrumentExtVersion> baseList) {
        if (CollectionUtil.isNotEmpty(currentList)) {
            return currentList.stream()
                    .filter(i -> tablekey1.equals(i.getInstrument()))
                    .max(Comparator.comparing(InstrumentExtVersion::getLogdatetime, Comparator.nullsLast(Comparator.naturalOrder())))
                    .orElse(null);
        }
        if (CollectionUtil.isNotEmpty(baseList)) {
            return baseList.stream()
                    .filter(i -> tablekey1.equals(i.getInstrument()))
                    .findFirst()
                    .orElse(null);
        }
        return null;
    }

    /**
     * 通过反射为对象字段赋值 (保持原有逻辑，泛型化)
     */
    public static <T> void populateInstrumentVersion(T t, Map<String, String> fieldValueMap) throws Exception {
        Class<?> clazz = t.getClass();
        Field[] fields = clazz.getDeclaredFields();

        for (Field field : fields) {
            String fieldName = field.getName();
            if (fieldValueMap.containsKey(fieldName)) {
                Object value = fieldValueMap.get(fieldName);
                field.setAccessible(true);
                Object convertedValue = convertValueToFieldType(value, field.getType());
                field.set(t, convertedValue);
            }
        }
    }

    /**
     * 类型转换逻辑
     */
    private static Object convertValueToFieldType(Object value, Class<?> fieldType) throws Exception {
        if (value == null) {
            return null;
        }
        String strVal = value.toString();
        if (fieldType == String.class) {
            return strVal;
        } else if (fieldType == BigDecimal.class) {
            return new BigDecimal(strVal);
        } else if (fieldType == Date.class) {
            SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd yyyy hh:mm a", Locale.ENGLISH);
            return dateFormat.parse(strVal);
        } else if (fieldType == Integer.class || fieldType == int.class) {
            return Integer.parseInt(strVal);
        } else if (fieldType == Long.class || fieldType == long.class) {
            return Long.parseLong(strVal);
        } else if (fieldType == Double.class || fieldType == double.class) {
            return Double.parseDouble(strVal);
        } else if (fieldType == Boolean.class || fieldType == boolean.class) {
            return Boolean.parseBoolean(strVal);
        } else {
            throw new IllegalArgumentException("Unsupported field type: " + fieldType.getName());
        }
    }

    /**
     * 迁移上下文配置记录
     */
    private static class MigrationContext {
        final Supplier<List<GenericLog>> insertQuery;
        final Supplier<List<GenericLog>> updateQuery;
        final Supplier<Object> creator;
        final TriFunction<String, List<?>, List<?>, Object> initializer;
        final java.util.function.Consumer<List<?>> saver;

        // 用于在 Insert 和 Update 步骤间传递已处理的数据
        List<?> currentResultList;

        public MigrationContext(Supplier<List<GenericLog>> insertQuery,
                                Supplier<List<GenericLog>> updateQuery,
                                Supplier<?> creator,
                                TriFunction<String, List<?>, List<?>, ?> initializer,
                                java.util.function.Consumer<List<?>> saver) {
            this.insertQuery = insertQuery;
            this.updateQuery = updateQuery;
            this.creator = (Supplier<Object>) creator;
            this.initializer = (TriFunction<String, List<?>, List<?>, Object>) initializer;
            this.saver = saver;
        }
    }

    /**
     * 自定义三参数函数式接口
     */
    @FunctionalInterface
    interface TriFunction<T, U, V, R> {
        R apply(T t, U u, V v);
    }
}
