package com.anzhi.raft.storage;

import com.anzhi.raft.core.LogEntry;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class FileLogModule implements LogModule {
    private static final Logger logger = LoggerFactory.getLogger(FileLogModule.class);

    private final Path logFile;
    private final List<LogEntry> logs;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final BufferedWriter writer;

    public FileLogModule(Path dataDir) {
        this.logFile = dataDir.resolve("log.entries");
        this.logs = new ArrayList<>();

        try {
            // 确保数据目录存在
            Files.createDirectories(dataDir);

            // 加载现有日志
            loadLogsFromFile();

            // 初始化用于追加写的 writer
            this.writer = Files.newBufferedWriter(
                    this.logFile,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND);

        } catch (IOException e) {
            logger.error("Failed to initialize FileLogModule at {}", dataDir, e);
            throw new RuntimeException("Failed to initialize FileLogModule", e);
        }
    }

    private void loadLogsFromFile() throws IOException {
        if (!Files.exists(logFile)) {
            logger.info("Log file not found, starting with a new log.");
            // 确保 dummy entry 存在
            logs.add(new LogEntry(0, 0, null));
            return;
        }

        logger.info("Loading logs from file: {}", logFile);
        List<String> lines = Files.readAllLines(logFile, StandardCharsets.UTF_8);
        for (String line : lines) {
            if (!line.trim().isEmpty()) {
                LogEntry entry = objectMapper.readValue(line, LogEntry.class);
                logs.add(entry);
            }
        }
        logger.info("Loaded {} log entries.", logs.size());
    }

    @Override
    public synchronized void append(LogEntry entry) {
        try {
            String jsonEntry = objectMapper.writeValueAsString(entry);
            writer.write(jsonEntry);
            writer.newLine();
            writer.flush(); // 强制刷盘，确保持久化
            logs.add(entry);
            logger.debug("Appended and flushed log entry at index {}", entry.getIndex());
        } catch (IOException e) {
            logger.error("Failed to append log entry at index {}", entry.getIndex(), e);
            throw new RuntimeException("Failed to append log", e);
        }
    }

    @Override
    public synchronized void truncate(long fromIndex) {
        if (fromIndex <= 0 || fromIndex > logs.size()) {
            return;
        }
        // 非常低效的截断实现：重写整个文件
        // 生产环境需要更高效的实现，例如通过新的日志段文件
        logger.warn("Truncating log from index {}. This is an inefficient operation.", fromIndex);
        List<LogEntry> newLogs = new ArrayList<>(logs.subList(0, (int) fromIndex));
        try {
            // 关闭现有 writer
            writer.close();

            // 重写文件
            List<String> lines = new ArrayList<>();
            for(LogEntry entry : newLogs) {
                lines.add(objectMapper.writeValueAsString(entry));
            }
            Files.write(logFile, lines, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

            // 重新打开 writer 用于追加
            BufferedWriter newWriter = Files.newBufferedWriter(
                    this.logFile,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.APPEND);

            // 更新内存状态
            this.logs.clear();
            this.logs.addAll(newLogs);

        } catch (IOException e) {
            throw new RuntimeException("Failed to truncate and rewrite log file", e);
        }
    }

    // 其他方法基本不变，直接操作内存中的 List
    @Override
    public LogEntry getEntry(long index) {
        if (index < 0 || index >= logs.size()) {
            return null;
        }
        return logs.get((int) index);
    }

    @Override
    public long getLastIndex() {
        if (logs.isEmpty()) {
            return 0;
        }
        return logs.get(logs.size() - 1).getIndex();
    }

    @Override
    public LogEntry getLastEntry() {
        if (logs.isEmpty()) {
            return null;
        }
        return logs.get(logs.size() - 1);
    }

    @Override
    public List<LogEntry> getEntries(long fromIndex) {
        if (fromIndex <= 0 || fromIndex > logs.size()) {
            return Collections.emptyList();
        }
        return new ArrayList<>(logs.subList((int) fromIndex, logs.size()));
    }
}
