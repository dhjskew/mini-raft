package com.anzhi.raft.rpc;


import java.io.Serializable;

public class RequestVoteArgs implements Serializable {
    private static final long serialVersionUID = 1L;

    private long term;          // 候选人的任期号
    private String candidateId; // 请求选票的候选人ID
    private long lastLogIndex;  // 候选人最后日志条目的索引值
    private long lastLogTerm;   // 候选人最后日志条目的任期号

    // Getters and Setters ...
    public long getTerm() { return term; }
    public void setTerm(long term) { this.term = term; }
    public String getCandidateId() { return candidateId; }
    public void setCandidateId(String candidateId) { this.candidateId = candidateId; }
    public long getLastLogIndex() { return lastLogIndex; }
    public void setLastLogIndex(long lastLogIndex) { this.lastLogIndex = lastLogIndex; }
    public long getLastLogTerm() { return lastLogTerm; }
    public void setLastLogTerm(long lastLogTerm) { this.lastLogTerm = lastLogTerm; }
}
