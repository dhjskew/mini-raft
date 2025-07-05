package com.anzhi.raft.rpc;

import java.io.Serializable;

public class RequestVoteResult implements Serializable {
    private static final long serialVersionUID = 1L;

    private long term;          // 当前任期号，以便候选人更新自己的任期
    private boolean voteGranted; // 候选人是否赢得此张选票

    // Getters and Setters ...
    public long getTerm() { return term; }
    public void setTerm(long term) { this.term = term; }
    public boolean isVoteGranted() { return voteGranted; }
    public void setVoteGranted(boolean voteGranted) { this.voteGranted = voteGranted; }
}
