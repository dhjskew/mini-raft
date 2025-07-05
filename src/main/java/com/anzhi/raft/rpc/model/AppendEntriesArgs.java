package com.anzhi.raft.rpc.model;

import com.anzhi.raft.core.LogEntry;
import java.io.Serializable;
import java.util.List;

public class AppendEntriesArgs implements Serializable {
    private static final long serialVersionUID = 1L;

    private long term;          // Leader 的任期
    private String leaderId;    // Leader ID
    private long prevLogIndex;  // 紧邻新日志条目之前的那个日志条目的索引
    private long prevLogTerm;   // 紧邻新日志条目之前的那个日志条目的任期
    private List<LogEntry> entries; // 需要被保存的日志条目（心跳时为空）
    private long leaderCommit;  // Leader 已知已提交的最高的日志条目的索引

    // Getters and Setters...
    public long getTerm() { return term; }
    public void setTerm(long term) { this.term = term; }
    public String getLeaderId() { return leaderId; }
    public void setLeaderId(String leaderId) { this.leaderId = leaderId; }
    public long getPrevLogIndex() { return prevLogIndex; }
    public void setPrevLogIndex(long prevLogIndex) { this.prevLogIndex = prevLogIndex; }
    public long getPrevLogTerm() { return prevLogTerm; }
    public void setPrevLogTerm(long prevLogTerm) { this.prevLogTerm = prevLogTerm; }
    public List<LogEntry> getEntries() { return entries; }
    public void setEntries(List<LogEntry> entries) { this.entries = entries; }
    public long getLeaderCommit() { return leaderCommit; }
    public void setLeaderCommit(long leaderCommit) { this.leaderCommit = leaderCommit; }
}
