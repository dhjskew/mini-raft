package com.anzhi.raft.rpc;

import com.anzhi.raft.Command;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.Serializable;

// 这个类作为客户端请求的载体
public class ClientRequest implements Serializable {
    private static final long serialVersionUID = 1L;
    private final Command command;

    @JsonCreator
    public ClientRequest(@JsonProperty("command") Command command) {
        this.command = command;
    }

    public Command getCommand() {
        return command;
    }
}
