package com.anzhi.raft;

import java.io.Serializable;

public class ClientResponse implements Serializable {
    private static final long serialVersionUID = 1L;

    private boolean success;
    private Object data;

    // 如果请求失败，因为当前节点不是 Leader，则会填充以下字段
    private String leaderId;
    private String leaderAddress;

    public static ClientResponse success(Object data) {
        ClientResponse response = new ClientResponse();
        response.success = true;
        response.data = data;
        return response;
    }

    public static ClientResponse failure(String leaderId, String leaderAddress) {
        ClientResponse response = new ClientResponse();
        response.success = false;
        response.leaderId = leaderId;
        response.leaderAddress = leaderAddress;
        return response;
    }

    // Getters and Setters...
    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }
    public Object getData() { return data; }
    public void setData(Object data) { this.data = data; }
    public String getLeaderId() { return leaderId; }
    public void setLeaderId(String leaderId) { this.leaderId = leaderId; }
    public String getLeaderAddress() { return leaderAddress; }
    public void setLeaderAddress(String leaderAddress) { this.leaderAddress = leaderAddress; }
}
