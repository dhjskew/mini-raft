package com.anzhi.raft;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryStateMachine implements StateMachine {
    private static final Logger logger = LoggerFactory.getLogger(InMemoryStateMachine.class);

    private final ConcurrentHashMap<String, String> map = new ConcurrentHashMap<>();

    @Override
    public void apply(Command command) {
        if (command == null) {
            return;
        }
        // 我们这里只实现一个简单的 SET 操作
        if ("SET".equalsIgnoreCase(command.getOperation())) {
            String key = command.getKey();
            String value = command.getValue();
            logger.info("Applying command: SET {} = {}", key, value);
            map.put(key, value);
        } else {
            logger.warn("Unsupported command operation: {}", command.getOperation());
        }
    }

    @Override
    public String get(String key) {
        return map.get(key);
    }
}
