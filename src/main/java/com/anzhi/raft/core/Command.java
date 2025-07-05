package com.anzhi.raft.core;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;

// 代表一个客户端命令
public class Command implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String operation;
    private final String key;
    private final String value;

    @JsonCreator
    public Command(
            @JsonProperty("operation") String operation,
            @JsonProperty("key") String key,
            @JsonProperty("value") String value) {
        this.operation = operation;
        this.key = key;
        this.value = value;
    }

    // Getters...
    public String getOperation() { return operation; }
    public String getKey() { return key; }
    public String getValue() { return value; }

    @Override
    public String toString() {
        return "Command{" +
                "operation='" + operation + '\'' +
                ", key='" + key + '\'' +
                ", value='" + value + '\'' +
                '}';
    }
}
