package com.anzhi.raft;

/**
 * 状态机接口
 */
public interface StateMachine {

    /**
     * 将命令应用到状态机
     * @param command 要应用的命令
     */
    void apply(Command command);

    /**
     * 从状态机获取一个键的值
     * @param key 键
     * @return 键对应的值
     */
    String get(String key);
}
