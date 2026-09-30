package com.metadata.engine.support;

import com.metadata.engine.model.CollectLogEntry;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 采集日志写入器
 */
public class CollectLogWriter {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final List<CollectLogEntry> entries = new ArrayList<>();

    private final ObjectMapper objectMapper;

    public CollectLogWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void info(String message) {
        append("INFO", message);
    }

    public void warn(String message) {
        append("WARN", message);
    }

    public void success(String message) {
        append("SUCCESS", message);
    }

    public void error(String message) {
        append("ERROR", message);
    }

    public List<CollectLogEntry> getEntries() {
        return entries;
    }

    public String toJson() {
        try {
            return objectMapper.writeValueAsString(entries);
        } catch (JsonProcessingException ex) {
            return "[]";
        }
    }

    /**
     * 提取失败原因：优先最后一条 ERROR 级别日志
     */
    public String resolveFailureMessage() {
        for (int i = entries.size() - 1; i >= 0; i--) {
            CollectLogEntry entry = entries.get(i);
            if ("ERROR".equals(entry.getLevel()) && StringUtils.isNotBlank(entry.getMsg())) {
                return entry.getMsg();
            }
        }
        return "采集失败";
    }

    private void append(String level, String message) {
        CollectLogEntry entry = new CollectLogEntry();
        entry.setTime(LocalDateTime.now().format(TIME_FORMATTER));
        entry.setLevel(level);
        entry.setMsg(message);
        entries.add(entry);
    }
}
