package com.example.migration.service.impl;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.ObjectUtil;
import com.example.migration.dao.master.entity.GenericLog;
import com.example.migration.dao.master.entity.InstrumentExtVersion;
import com.example.migration.dao.master.entity.InstrumentVersion;
import com.example.migration.dao.master.mapper.GenericLogMapper;
import com.example.migration.pojo.logMigration.req.MigrationReq;
import com.example.migration.service.InstrumentExtVersionService;
import com.example.migration.service.InstrumentVersionService;
import com.example.migration.service.LogRecordMigrationService;
import com.example.migration.util.CaseUtil;
import com.alibaba.excel.util.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
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

    private List<String> errorInsertLogs = Collections.synchronizedList(new ArrayList<>());

    private List<String> errorUpdateLogs = Collections.synchronizedList(new ArrayList<>());

    @Override
    //@Transactional(rollbackFor = Exception.class,value = "masterTransactionManager")
    public String migration(MigrationReq req) {
        String tableName = req.getTableName();
        Date currentDate = new Date();

        // 获取对应的迁移上下文配置
        MigrationContext context = getMigrationContext(tableName);
        if (context == null) {
            throw new IllegalArgumentException("Unsupported table name: " + tableName);
        }

        // 处理插入数据
        List<String> keys = context.insertQueryGroup.get().stream().filter(StringUtils::isNotBlank).collect(Collectors.toList());
        int threadCount = Runtime.getRuntime().availableProcessors();

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        int batch = 1000;
        try {
            for (int i = 0; i < keys.size(); i += batch) {
                int end = Math.min(i + batch, keys.size());
                List<String> subList = keys.subList(i, end);
                CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                    // 执行具体的查询和处理逻辑
                    List<GenericLog> insertList = context.insertQuery.apply(subList);
                    if (CollectionUtil.isNotEmpty(insertList)) {
                        processMigration(insertList, currentDate, context, true);
                    }
                }, executor);
                futures.add(future);
            }
            // 阻塞主线程，直到所有批次任务完成
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        }finally {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        ExecutorService executorUpd = Executors.newFixedThreadPool(threadCount);
        List<CompletableFuture<Void>> futuresUpd = new ArrayList<>();

        List<String> updKeys = context.updateQueryGroup.get().stream().filter(StringUtils::isNotBlank).collect(Collectors.toList());
        int updBatch = 1000;
        try {
            for (int i = 0; i < updKeys.size(); i += updBatch) {
                int end = Math.min(i + updBatch, updKeys.size());
                List<String> subList = updKeys.subList(i, end);
                CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                    List<GenericLog> updateList = context.updateQuery.apply(subList);
                    if (CollectionUtil.isNotEmpty(updateList)) {
                        processMigration(updateList, currentDate, context, false);
                    }
                    }, executorUpd);
                futuresUpd.add(future);
            }
            // 阻塞主线程，直到所有批次任务完成
            CompletableFuture.allOf(futuresUpd.toArray(new CompletableFuture[0])).join();
        } finally {
            executorUpd.shutdown();
            try {
                if (!executorUpd.awaitTermination(60, TimeUnit.SECONDS)) {
                    executorUpd.shutdownNow();
                }
            } catch (InterruptedException e) {
                executorUpd.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        // 记录文件
        if (CollectionUtil.isNotEmpty(errorInsertLogs)) {
            FileUtil.writeUtf8Lines(errorInsertLogs,tableName + "_errorInsertLogs.txt");
        }
        if (CollectionUtil.isNotEmpty(errorUpdateLogs)) {
            FileUtil.writeUtf8Lines(errorUpdateLogs, tableName + "_errorUpdateLogs.txt");
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
                .filter(e -> ObjectUtil.isNotEmpty(e.getTablekey1()))
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
                        .filter(e -> e.getAftervalue() != null)
                        .collect(Collectors.toMap(e -> CaseUtil.toLowerCamelCase(e.getLogcolumn()), GenericLog::getAftervalue, (e1, e2) -> e1));

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
                    e.printStackTrace();
                    List<String> keys = logList.stream().map(GenericLog::getTablekey1).distinct().collect(Collectors.toList());
                    if (isInsert) {
                        errorInsertLogs.addAll(keys);
                    }else {
                        errorUpdateLogs.addAll(keys);
                    }
                }
            }
        }

        // 保存结果
        if (CollectionUtil.isNotEmpty(resultList)) {
            //context.saver.accept(resultList);
            // 如果是 Insert 阶段，将结果存入上下文供 Update 阶段使用
            if (isInsert) {
                context.currentResultList.addAll(resultList);
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
                    (keys) -> genericLogMapper.queryInstrumentVersionInsert(keys),
                    (keys) -> genericLogMapper.queryInstrumentVersionUpdate(keys),
                    () -> genericLogMapper.queryInstrumentVersionInsertGruopKey(),
                    () -> genericLogMapper.queryInstrumentVersionUpdateGruopKey(),
                    InstrumentVersion::new,
                    (tablekey1, resultList, baseList) -> initInstrumentVersionHandle(tablekey1, (List<InstrumentVersion>) resultList, (List<InstrumentVersion>) baseList),
                    (list) -> instrumentVersionService.saveBatch((List<InstrumentVersion>) list)
            );
        } else if ("InstrumentExt".equals(tableName)) {
            return new MigrationContext(
                    (keys) -> genericLogMapper.queryInstrumentExtVersionInsert(keys),
                    (keys) -> genericLogMapper.queryInstrumentExtVersionUpdate(keys),
                    () -> genericLogMapper.queryInstrumentVersionExtInsertGruopKey(),
                    () -> genericLogMapper.queryInstrumentVersionExtUpdateGruopKey(),
                    InstrumentExtVersion::new,
                    (tablekey1, resultList, baseList) -> initInstrumentExtVersionHandle(tablekey1, (List<InstrumentExtVersion>) resultList, (List<InstrumentExtVersion>) baseList),
                    (list) -> instrumentExtVersionService.saveBatch((List<InstrumentExtVersion>) list)
            );
        }
        return null;
    }

    // --- 原有的初始化逻辑保持不变，仅调整泛型签名以适配函数式接口 ---

    private InstrumentVersion initInstrumentVersionHandle(String tablekey1, List<InstrumentVersion> currentList, List<InstrumentVersion> baseList) {
        InstrumentVersion version = null;
        if (CollectionUtil.isNotEmpty(currentList)) {
            version = currentList.stream()
                    .filter(i -> tablekey1.equals(i.getInstrument()))
                    .max(Comparator.comparing(InstrumentVersion::getLogdatetime, Comparator.nullsLast(Comparator.naturalOrder())))
                    .orElse(null);
        }
        if (version == null && CollectionUtil.isNotEmpty(baseList)) {
            version = baseList.stream()
                    .filter(i -> tablekey1.equals(i.getInstrument()))
                    .findFirst()
                    .orElse(null);
        }
        return version;
    }

    private InstrumentExtVersion initInstrumentExtVersionHandle(String tablekey1, List<InstrumentExtVersion> currentList, List<InstrumentExtVersion> baseList) {
        InstrumentExtVersion extVersion = null;
        if (CollectionUtil.isNotEmpty(currentList)) {
            extVersion = currentList.stream()
                    .filter(i -> tablekey1.equals(i.getInstrument()))
                    .max(Comparator.comparing(InstrumentExtVersion::getLogdatetime, Comparator.nullsLast(Comparator.naturalOrder())))
                    .orElse(null);
        }
        if (extVersion == null && CollectionUtil.isNotEmpty(baseList)) {
            extVersion = baseList.stream()
                    .filter(i -> tablekey1.equals(i.getInstrument()))
                    .findFirst()
                    .orElse(null);
        }
        return extVersion;
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
            strVal = strVal.replaceAll("\\s+", " ");
            // 2. 在时间和 AM/PM 之间添加空格 (解决 "10:58AM" 问题)
            // 正则解释：查找数字后紧跟 AM 或 PM 的情况，并在中间插入空格
            strVal = strVal.replaceAll("(\\d)([AaPp][Mm])", "$1 $2");
            // 定义可能的日期格式数组
            String[] datePatterns = {
                    "MMM dd yyyy h:mm a",   // 【新增】匹配 "Sep 17 2025 9:05 PM"
                    "dd MMM yyyy",       // 匹配 "20 Aug 2035"
                    "MMM dd yyyy hh:mm a", // 匹配 "Oct 21 2025 10:54 AM"
                    "yyyy-MM-dd",        // 匹配 "2025-08-20"
                    "yyyy/MM/dd",        // 匹配 "2025/08/20"
                    "dd/MM/yyyy"         // 匹配 "20/08/2035"
            };

            for (String pattern : datePatterns) {
                try {
                    SimpleDateFormat dateFormat = new SimpleDateFormat(pattern, Locale.ENGLISH);
                    dateFormat.setLenient(false); // 严格匹配
                    return dateFormat.parse(strVal);
                } catch (ParseException e) {
                    // 尝试下一个格式
                }
            }
            // 如果所有格式都失败，抛出异常
            throw new ParseException("Unable to parse date: " + strVal + " with any known format", 0);
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
        final Function<List<String>,List<GenericLog>> insertQuery;
        final Function<List<String>,List<GenericLog>> updateQuery;

        final Supplier<List<String>> insertQueryGroup;
        final Supplier<List<String>> updateQueryGroup;
        final Supplier<Object> creator;
        final TriFunction<String, List<?>, List<?>, Object> initializer;
        final java.util.function.Consumer<List<?>> saver;

        // 用于在 Insert 和 Update 步骤间传递已处理的数据
        List<Object> currentResultList = Collections.synchronizedList(new ArrayList<>());

        public MigrationContext(Function<List<String>,List<GenericLog>> insertQuery,
                                Function<List<String>,List<GenericLog>> updateQuery,
                                Supplier<List<String>> insertQueryGroup,
                                Supplier<List<String>> updateQueryGroup,
                                Supplier<?> creator,
                                TriFunction<String, List<?>, List<?>, ?> initializer,
                                java.util.function.Consumer<List<?>> saver) {
            this.insertQuery = insertQuery;
            this.updateQuery = updateQuery;
            this.insertQueryGroup = insertQueryGroup;
            this.updateQueryGroup = updateQueryGroup;
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
