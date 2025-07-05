package com.anzhi.raft.rpc;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;

public class JsonSerializer {

    // ObjectMapper 是线程安全的，可以作为单例重复使用
    private static final ObjectMapper objectMapper = new ObjectMapper();

    static {
        // 注册 JSR-310 模块以支持 Java 8 的日期和时间类型
        objectMapper.registerModule(new JavaTimeModule());
    }

    public byte[] serialize(Object object) {
        try {
            return objectMapper.writeValueAsBytes(object);
        } catch (JsonProcessingException e) {
            // 在实际应用中，这里应该抛出一个自定义的运行时异常
            throw new RuntimeException("Error serializing object to JSON", e);
        }
    }

    public <T> T deserialize(byte[] data, Class<T> clazz) {
        try {
            return objectMapper.readValue(data, clazz);
        } catch (IOException e) {
            throw new RuntimeException("Error deserializing JSON to object", e);
        }
    }
}
