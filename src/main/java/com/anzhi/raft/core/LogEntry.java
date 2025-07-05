package com.anzhi.raft.core;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;

// 目前只是一个简单的占位符，后续会添加 command 等字段
public class LogEntry implements Serializable {
    private static final long serialVersionUID = 1L;

    private long term;
    private long index;
    private final Command command;

    @JsonCreator
    public LogEntry(
            @JsonProperty("term") long term,
            @JsonProperty("index") long index,
            @JsonProperty("command") Command command) {
        this.term = term;
        this.index = index;
        this.command = command;
    }

    // Getters and Setters...
    public long getTerm() { return term; }
    public void setTerm(long term) { this.term = term; }
    public long getIndex() { return index; }
    public void setIndex(long index) { this.index = index; }
    public Command getCommand() { return command; }

}
