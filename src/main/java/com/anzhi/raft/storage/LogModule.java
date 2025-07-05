package com.anzhi.raft.storage;

import com.anzhi.raft.core.LogEntry;

import java.util.List;

/**
 * 日志模块接口
 */
public interface LogModule {

    /**
     * 追加一个日志条目
     * @param entry 日志条目
     */
    void append(LogEntry entry);

    /**
     * 根据索引获取日志条目
     * @param index 索引 (从1开始)
     * @return 日志条目，如果不存在则返回 null
     */
    LogEntry getEntry(long index);

    /**
     * 获取最后一个日志条目
     * @return 最后一个日志条目，如果日志为空则返回 null
     */
    LogEntry getLastEntry();

    /**
     * 获取最后一个日志的索引
     * @return 最后一个日志的索引，如果日志为空则返回 0
     */
    long getLastIndex();

    /**
     * 从指定索引处（包含该索引）截断日志
     * @param fromIndex 要开始截断的索引 (从1开始)
     */
    void truncate(long fromIndex);

    /**
     * 获取从指定索引开始的所有日志条目
     * @param fromIndex 开始索引 (从1开始)
     * @return 日志条目列表
     */
    List<LogEntry> getEntries(long fromIndex);
}
