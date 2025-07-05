package com.anzhi.raft;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 用于持久化元数据的 POJO (Plain Old Java Object)
 */
public class PersistentState {

    private final long currentTerm;
    private final String votedFor;

    @JsonCreator
    public PersistentState(
            @JsonProperty("currentTerm") long currentTerm,
            @JsonProperty("votedFor") String votedFor) {
        this.currentTerm = currentTerm;
        this.votedFor = votedFor;
    }

    public long getCurrentTerm() {
        return currentTerm;
    }

    public String getVotedFor() {
        return votedFor;
    }
}
