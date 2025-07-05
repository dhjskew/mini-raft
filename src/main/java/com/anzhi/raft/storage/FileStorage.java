package com.anzhi.raft.storage;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public class FileStorage {
    private static final Logger logger = LoggerFactory.getLogger(FileStorage.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 将元数据状态以原子方式保存到文件。
     * 实现方式：先写入临时文件，然后重命名为目标文件。
     */
    public static void save(PersistentState state, Path file) throws IOException {
        Path tempFile = file.resolveSibling(file.getFileName() + ".tmp");
        try {
            byte[] jsonBytes = objectMapper.writeValueAsBytes(state);
            Files.write(tempFile, jsonBytes);
            // ATOMIC_MOVE 保证了操作的原子性
            Files.move(tempFile, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            logger.debug("Successfully saved persistent state to {}", file);
        } catch (IOException e) {
            logger.error("Failed to save persistent state to {}", file, e);
            throw e;
        } finally {
            // 确保临时文件在操作失败时被删除
            Files.deleteIfExists(tempFile);
        }
    }

    /**
     * 从文件加载元数据状态。
     * 如果文件不存在，返回一个初始化的默认状态。
     */
    public static PersistentState load(Path file) {
        if (!Files.exists(file)) {
            logger.warn("Persistent state file {} not found, returning default state.", file);
            return new PersistentState(0, null);
        }
        try {
            byte[] jsonBytes = Files.readAllBytes(file);
            return objectMapper.readValue(jsonBytes, PersistentState.class);
        } catch (IOException e) {
            logger.error("Failed to load persistent state from {}, returning default state.", file, e);
            // 如果加载失败，返回默认状态以避免启动失败
            return new PersistentState(0, null);
        }
    }
}
