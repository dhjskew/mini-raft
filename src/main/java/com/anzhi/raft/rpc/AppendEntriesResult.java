package com.anzhi.raft.rpc;

import java.io.Serializable;

public class AppendEntriesResult implements Serializable {
    private static final long serialVersionUID = 1L;

    private long term;      // 当前任期，用于 Leader 更新自己
    private boolean success; // 如果 Follower 包含了匹配 prevLogIndex 和 prevLogTerm 的日志条目，则为 true

    // Getters and Setters...
    public long getTerm() { return term; }
    public void setTerm(long term) { this.term = term; }
    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }
}
