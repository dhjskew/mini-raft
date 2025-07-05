package com.anzhi.raft.storage;

import com.anzhi.raft.core.LogEntry;

import java.util.ArrayList;
import java.util.List;

public class InMemoryLogModule implements LogModule {

    // 使用 ArrayList 来存储日志
    // Raft 的日志索引从 1 开始，所以我们在第 0 个位置放一个 "dummy" entry
    private final List<LogEntry> logs = new ArrayList<>();

    public InMemoryLogModule() {
        // dummy entry at index 0, with term 0
        // 这使得日志索引与列表索引的转换更方便
        logs.add(new LogEntry(0, 0,null));
    }

    @Override
    public void append(LogEntry entry) {
        logs.add(entry);
    }

    @Override
    public LogEntry getEntry(long index) {
        if (index <= 0 || index >= logs.size()) {
            return null;
        }
        return logs.get((int) index);
    }

    @Override
    public LogEntry getLastEntry() {
        if (logs.size() <= 1) {
            return null; // 只有 dummy entry
        }
        return logs.get(logs.size() - 1);
    }

    @Override
    public long getLastIndex() {
        if (logs.size() <= 1) {
            return 0;
        }
        return logs.get(logs.size() - 1).getIndex();
    }

    @Override
    public void truncate(long fromIndex) {
        if (fromIndex <= 0) {
            return;
        }
        // subList is a view, clear() will affect the original list
        if (fromIndex < logs.size()) {
            logs.subList((int) fromIndex, logs.size()).clear();
        }
    }

    @Override
    public List<LogEntry> getEntries(long fromIndex) {
        if (fromIndex <= 0 || fromIndex > logs.size()) {
            return new ArrayList<>();
        }
        return new ArrayList<>(logs.subList((int) fromIndex, logs.size()));
    }
}
