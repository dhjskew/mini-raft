package com.anzhi.raft.rpc;

import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.io.Serializable;

public class RpcMessage implements Serializable {
    private static final long serialVersionUID = 1L;

    // 唯一请求ID，用于匹配请求和响应
    private String requestId;
    // 消息类型，例如 "RequestVote" 或 "AppendEntries"
    private String messageType;
    @JsonTypeInfo(use = JsonTypeInfo.Id.CLASS)
    private Object payload;

    // Getters and Setters...
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public String getMessageType() { return messageType; }
    public void setMessageType(String messageType) { this.messageType = messageType; }
    public Object getPayload() { return payload; }
    public void setPayload(Object payload) { this.payload = payload; }
}
