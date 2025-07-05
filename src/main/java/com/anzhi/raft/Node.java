package com.anzhi.raft;

import com.anzhi.raft.rpc.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
// ... (其他 import)
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;


public class Node {
    private static final Logger logger = LoggerFactory.getLogger(Node.class);

    public boolean isLeader() { return state == NodeState.LEADER; }
    // 添加一个 getter 方法方便 Main 方法验证
    public StateMachine getStateMachine() {
        return stateMachine;
    }
    private volatile String currentLeaderId; // **新增：缓存当前 Leader 的 ID**
    // --- Raft 核心状态 ---
    private final String selfId; // 节点自身ID
    private volatile NodeState state; // 节点当前状态，volatile保证线程可见性
    private final Map<String, String> peerAddresses; // 其他节点的ID和地址(ip:port)

    // --- 持久化状态 (所有服务器) ---
    private volatile long currentTerm;
    private volatile String votedFor; // 在当前任期投票给谁
    private final LogModule logModule; // ***** MODIFIED *****: 接口不变，实现将改变

    // ***** NEW *****: 文件路径相关
    private final Path dataDir;
    private final Path metadataFile;

    // --- 易失性状态 (所有服务器) ---
    private AtomicInteger votesReceived; // 作为 Candidate 收到的票数
    private volatile long commitIndex; // 已知的最大已提交日志条目的索引

    // --- 易失性状态 (仅在 Leader 上) ---
    private Map<String, Long> nextIndex;  // 对于每一个服务器，需要发送给它的下一个日志条目的索引值
    private Map<String, Long> matchIndex; // 对于每一个服务器，已经复制给它的日志的最高索引值


    // --- 定时器与线程池 ---
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private Future<?> electionTimerFuture; // 用于持有选举定时任务的Future，方便取消
    private Future<?> heartbeatTimerFuture; // 新增心跳定时器 Future

    // --- 状态机相关 ---
    private final StateMachine stateMachine;
    private volatile long lastApplied; // 已应用到状态机的最高日志条目的索引
    private Future<?> applierFuture;   // 应用者任务的 Future


    // --- 配置 ---
    private final long electionTimeoutMinMs = 250;
    private final long electionTimeoutMaxMs = 400;
    private final long heartbeatIntervalMs = 50; // 心跳间隔必须远小于选举超时时间


    private final RpcClient rpcClient = RpcClient.getInstance();

    public Node(String selfId, Map<String, String> peerAddresses, Path dataDir) {
        this.selfId = selfId;
        this.peerAddresses = peerAddresses;
        this.dataDir = dataDir;
        this.metadataFile = dataDir.resolve("metadata.json");

        try {
            Files.createDirectories(dataDir);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create data directory", e);
        }

        // 加载持久化状态
        loadPersistentState();

        // 使用 FileLogModule
        this.logModule = new FileLogModule(dataDir);

        this.stateMachine = new InMemoryStateMachine();
        this.commitIndex = 0;
        this.lastApplied = 0;
    }

    public void start() {
        logger.info("Node {} starting...", selfId);
        // 节点启动时，初始状态为 FOLLOWER
        becomeFollower(0);

        // **启动应用者任务**
        // 每 10ms 检查一次是否有可应用的日志
        this.applierFuture = scheduler.scheduleAtFixedRate(
                this::applyCommittedEntries,
                10,
                10,
                TimeUnit.MILLISECONDS);
    }

    public void shutdown() {
        logger.info("Node {} shutting down...", selfId);
        if (applierFuture != null) {
            applierFuture.cancel(true);
        }
        scheduler.shutdownNow();
        RpcClient.getInstance().shutdown();
    }

    // ===================================================================
    //                     状态切换核心逻辑 (Task 1.1)
    // ===================================================================

    private void becomeFollower(long term) {
        logger.info("Node {} is becoming Follower for term {}", selfId, term);
        this.state = NodeState.FOLLOWER;
        this.currentTerm = term;
        this.votedFor = null; // 新任期开始，清空投票记录
        persistState(); // <--- 添加持久化调用

        // 如果之前是 Leader 或 Candidate，需要取消心跳或选举定时器
        if (heartbeatTimerFuture != null) {
            heartbeatTimerFuture.cancel(true);
        }
        resetElectionTimer();
    }

    private void becomeCandidate() {
        // 增加任期并切换状态
        this.currentTerm++;
        logger.info("Node {} is becoming Candidate for term {}", selfId, this.currentTerm);
        this.state = NodeState.CANDIDATE;

        // 投票给自己
        this.votedFor = selfId;
        this.votesReceived = new AtomicInteger(1);
        persistState(); // <--- 添加持久化调用

        // 重置选举定时器
        resetElectionTimer();

        // 构建投票请求
        RequestVoteArgs args = new RequestVoteArgs();
        args.setTerm(this.currentTerm);
        args.setCandidateId(this.selfId);

        // ***** 关键修改：从日志模块获取真实的日志信息 *****
        LogEntry lastEntry = logModule.getLastEntry();
        if (lastEntry != null) {
            args.setLastLogIndex(lastEntry.getIndex());
            args.setLastLogTerm(lastEntry.getTerm());
        } else {
            args.setLastLogIndex(0);
            args.setLastLogTerm(0);
        }

        // 向所有其他节点广播投票请求
        for (String peerId : peerAddresses.keySet()) {
            String address = peerAddresses.get(peerId);
            String host = address.split(":")[0];
            int port = Integer.parseInt(address.split(":")[1]);

            logger.debug("Sending RequestVote to {} at {}", peerId, address);

            // 异步发送请求
            rpcClient.sendRequest(host, port, args).whenCompleteAsync((response, error) -> {
                if (error != null) {
                    logger.error("Failed to send RequestVote to {}: {}", peerId, error.getMessage());
                    return;
                }

                handleVoteResult(response);
            }, scheduler); // 使用节点的调度器线程来处理回调，保证线程安全
        }
    }

    private void becomeLeader() {
        // 只有 Candidate 才能成为 Leader
        if (this.state != NodeState.CANDIDATE) {
            logger.warn("Node {} is not a candidate, cannot become a leader.", selfId);
            return;
        }
        logger.info("Node {} is becoming LEADER for term {}!", selfId, this.currentTerm);
        this.state = NodeState.LEADER;

        // 成为 Leader 后，取消选举定时器
        if (this.electionTimerFuture != null) {
            this.electionTimerFuture.cancel(true);
        }

        // 初始化 nextIndex 和 matchIndex
        // (注意：日志模块实现后，这里的 lastLogIndex 需要动态获取)
        long lastLogIndex = 0;
        this.nextIndex = new HashMap<>();
        this.matchIndex = new HashMap<>();
        for (String peerId : peerAddresses.keySet()) {
            nextIndex.put(peerId, lastLogIndex + 1);
            matchIndex.put(peerId, 0L);
        }

        // **立即发送第一轮心跳**，以宣告领导地位
        replicateLogsToFollowers();

        // **启动周期性心跳定时器**
        heartbeatTimerFuture = scheduler.scheduleAtFixedRate(
                this::replicateLogsToFollowers,
                heartbeatIntervalMs,
                heartbeatIntervalMs,
                TimeUnit.MILLISECONDS
        );
    }

    // ... (后续将添加 RPC 处理方法)
    // ===================================================================
    //                      选举定时器逻辑 (Task 1.2)
    // ===================================================================

    /**
     * 重置选举定时器
     */
    private void resetElectionTimer() {
        // 如果已存在一个定时任务，先取消它
        if (electionTimerFuture != null) {
            electionTimerFuture.cancel(true);
        }

        // 设置一个随机的选举超时时间，这是 Raft 的关键设计，可以有效避免“选票瓜分”
        long randomTimeout = ThreadLocalRandom.current().nextLong(electionTimeoutMinMs, electionTimeoutMaxMs + 1);

        // 安排一个新的超时任务
        electionTimerFuture = scheduler.schedule(this::handleElectionTimeout, randomTimeout, TimeUnit.MILLISECONDS);
        logger.debug("Node {} election timer reset, next timeout in {} ms", selfId, randomTimeout);
    }

    /**
     * 当选举超时发生时，由此方法处理
     */
    private void handleElectionTimeout() {
        // 只有 Follower 和 Candidate 会关心选举超时
        if (state == NodeState.LEADER) {
            logger.warn("Leader {} should not have an election timeout.", selfId);
            return;
        }

        logger.info("Node {} election timeout reached, starting new election...", selfId);

        // 超时后，节点状态变为 Candidate，并发起新一轮选举
        becomeCandidate();
    }


    private void replicateLogsToFollowers() {
        if (state != NodeState.LEADER) {
            return;
        }
        logger.debug("Leader {} replicating logs to followers...", selfId);

        for (String peerId : peerAddresses.keySet()) {
            long nextIdx = nextIndex.get(peerId);

            // 获取需要发送的日志条目
            List<LogEntry> entriesToSend = logModule.getEntries(nextIdx);

            // ***** 关键修复：在这里处理 prevEntry 可能为 null 的情况 *****
            LogEntry prevEntry = logModule.getEntry(nextIdx - 1);
            long prevLogIndex;
            long prevLogTerm;

            // 如果 prevEntry 为 null，说明是从头开始复制，或者前面的日志已被快照截断
            // 此时，prevLogIndex 和 prevLogTerm 都应为 0
            if (prevEntry != null) {
                prevLogIndex = prevEntry.getIndex();
                prevLogTerm = prevEntry.getTerm();
            } else {
                prevLogIndex = 0;
                prevLogTerm = 0;
            }

            // 构造 RPC 请求
            AppendEntriesArgs args = new AppendEntriesArgs();
            args.setTerm(this.currentTerm);
            args.setLeaderId(this.selfId);
            args.setPrevLogIndex(prevLogIndex); // 使用我们安全获取到的值
            args.setPrevLogTerm(prevLogTerm);   // 使用我们安全获取到的值
            args.setEntries(entriesToSend);
            args.setLeaderCommit(this.commitIndex);

            String address = peerAddresses.get(peerId);
            String host = address.split(":")[0];
            int port = Integer.parseInt(address.split(":")[1]);

            rpcClient.sendRequest(host, port, args).whenCompleteAsync((response, error) -> {
                if (error != null) {
                    logger.error("Failed to send AppendEntries to {}: {}", peerId, error.getMessage());
                    return;
                }
                handleAppendEntriesResult((AppendEntriesResult) response, peerId, entriesToSend);
            }, scheduler);
        }
    }

    private void applyCommittedEntries() {
        try {
            // 不断尝试应用已提交但未应用的日志
            while (commitIndex > lastApplied) {
                long indexToApply = lastApplied + 1;
                LogEntry entry = logModule.getEntry(indexToApply);
                if (entry != null) {
                    logger.info("Node {} applying entry at index {}", selfId, indexToApply);
                    stateMachine.apply(entry.getCommand());
                    lastApplied = indexToApply; // 更新 lastApplied 索引
                } else {
                    // 如果获取不到日志，可能是因为日志还在传输中，稍后重试
                    break;
                }
            }
        } catch (Exception e) {
            logger.error("Error applying committed entries on node {}", selfId, e);
        }
    }

    // ===================================================================
    //                      RPC 请求处理逻辑 (Task 1.4)
    // ===================================================================

    /**
     * 处理来自候选人的投票请求
     * @param args 候选人发来的请求参数
     * @return 投票结果
     */
    public RequestVoteResult handleRequestVote(RequestVoteArgs args) {
        // 探针 4: 检查业务逻辑是否被调用
//        logger.info(">>>>> Node {} is now handling RequestVote from {}", selfId, args.getCandidateId());
        RequestVoteResult result = new RequestVoteResult();
        result.setVoteGranted(false); // 默认不投票

        // 规则 1: 如果请求的任期小于当前任期，直接拒绝
        if (args.getTerm() < this.currentTerm) {
            result.setTerm(this.currentTerm);
            logger.info("Node {} rejected vote for {} (term {} < currentTerm {})",
                    selfId, args.getCandidateId(), args.getTerm(), this.currentTerm);
            return result;
        }

        // 如果收到任期更高的请求，无论当前是什么状态，都立刻转为 Follower
        if (args.getTerm() > this.currentTerm) {
            logger.info("Node {} discovered higher term {} from RequestVote, becoming follower.",
                    selfId, args.getTerm());
            becomeFollower(args.getTerm());
        }

        result.setTerm(this.currentTerm);

        // 规则 2: 在同一任期内，遵循"先到先得"原则
        // 如果 votedFor 为空 (还没投过票) 或者已经投给了该候选人 (处理重复请求)
        boolean canVote = (this.votedFor == null || this.votedFor.equals(args.getCandidateId()));

        if (!canVote) {
            logger.info("Node {} rejected vote for {} (already voted for {} in term {})",
                    selfId, args.getCandidateId(), this.votedFor, this.currentTerm);
            return result;
        }

        // 规则 3: 候选人的日志必须至少和自己一样新

        boolean logIsUpToDate = isLogUpToDate(args.getLastLogTerm(), args.getLastLogIndex());
        if (!logIsUpToDate) {
            logger.info("Node {} rejected vote for {} (log is not up-to-date)", selfId, args.getCandidateId());
            return result;
        }

        // 如果以上所有检查都通过，就投票给它
        logger.info("Node {} GRANTED vote for {} in term {}", selfId, args.getCandidateId(), this.currentTerm);
        this.votedFor = args.getCandidateId();
        persistState(); // <--- 添加持久化调用
        result.setVoteGranted(true);

        // 投票后，重置自己的选举定时器，因为该候选人有可能成为 Leader
        resetElectionTimer();

        return result;
    }

    private void handleVoteResult(Object response) {
        RequestVoteResult result = (RequestVoteResult) response;

        // 如果对方的任期比我大，说明我已经过时了，立刻转为 Follower
        if (result.getTerm() > this.currentTerm) {
            logger.info("Discovered higher term {} from vote result, becoming follower.", result.getTerm());
            becomeFollower(result.getTerm());
            return;
        }

        // 只处理当前任期的投票结果
        if (result.getTerm() == this.currentTerm && state == NodeState.CANDIDATE) {
            if (result.isVoteGranted()) {
                int currentVotes = votesReceived.incrementAndGet();
                logger.info("Vote granted. Total votes received: {}", currentVotes);

                // 检查是否获得了多数票
                // 加上自己的一票，所以是 > total_nodes / 2
                if (currentVotes > (peerAddresses.size() + 1) / 2) {
                    logger.info("Received majority of votes. Becoming leader...");
                    becomeLeader();
                }
            }
        }
    }

    public AppendEntriesResult handleAppendEntries(AppendEntriesArgs args) {
        AppendEntriesResult result = new AppendEntriesResult();
        result.setSuccess(false);

        if (args.getTerm() < this.currentTerm) {
            result.setTerm(this.currentTerm);
            return result;
        }

        resetElectionTimer();
        this.currentLeaderId = args.getLeaderId();

        if (args.getTerm() > this.currentTerm) {
            becomeFollower(args.getTerm());
        }
        result.setTerm(this.currentTerm);

        // ***** 关键修复 1: 正确处理 prevLogIndex = 0 的情况 *****
        // 如果 prevLogIndex 为 0，我们认为它与任何 Follower 的日志都是匹配的
        if (args.getPrevLogIndex() > 0) {
            LogEntry prevEntry = logModule.getEntry(args.getPrevLogIndex());
            if (prevEntry == null || prevEntry.getTerm() != args.getPrevLogTerm()) {
                logger.warn("Node {} consistency check failed at index {}. Local term: {}, Leader term: {}",
                        selfId, args.getPrevLogIndex(), prevEntry == null ? "null" : prevEntry.getTerm(), args.getPrevLogTerm());
                return result;
            }
        }

        if (!args.getEntries().isEmpty()) {
            logger.info("Node {} received {} new entries from leader.", selfId, args.getEntries().size());

            for (LogEntry entry : args.getEntries()) {
                if (logModule.getEntry(entry.getIndex()) != null && logModule.getEntry(entry.getIndex()).getTerm() != entry.getTerm()) {
                    logger.warn("Found conflict at index {}, truncating log.", entry.getIndex());
                    logModule.truncate(entry.getIndex());
                    break;
                }
            }

            for (LogEntry entry : args.getEntries()) {
                if (logModule.getEntry(entry.getIndex()) == null) {
                    logModule.append(entry);
                }
            }
        }

        if (args.getLeaderCommit() > this.commitIndex) {
            this.commitIndex = Math.min(args.getLeaderCommit(), logModule.getLastIndex());
            logger.info("Follower {} updated commitIndex to {}", selfId, this.commitIndex);
            // TODO: 应用日志到状态机
        }

        result.setSuccess(true);
        return result;
    }

    private void handleAppendEntriesResult(AppendEntriesResult result, String peerId, List<LogEntry> sentEntries) {
        // 如果响应的任期大于当前任期，说明自己已经过时，立刻下台
        if (result.getTerm() > this.currentTerm) {
            logger.info("Discovered higher term {} from AppendEntries result from {}, becoming follower.", result.getTerm(), peerId);
            becomeFollower(result.getTerm());
            return;
        }

        if (state == NodeState.LEADER && result.getTerm() == this.currentTerm) {
            if (result.isSuccess()) {
                // Follower 成功追加了日志
                if (!sentEntries.isEmpty()) {
                    long lastSentIndex = sentEntries.get(sentEntries.size() - 1).getIndex();
                    nextIndex.put(peerId, lastSentIndex + 1);
                    matchIndex.put(peerId, lastSentIndex);
                    logger.info("Successfully replicated log up to index {} to {}", lastSentIndex, peerId);

                    // 成功复制后，尝试推进 commitIndex
                    advanceCommitIndex();
                }
            } else {
                // Follower 拒绝了，因为日志不一致
                // Raft 的优化：可以直接将 nextIndex 回退到 Follower 的 term
                // 这里我们先用简单的-1策略
                long currentNextIndex = nextIndex.get(peerId);
                nextIndex.put(peerId, Math.max(1, currentNextIndex - 1));
                logger.warn("Follower {} rejected AppendEntries. Decrementing nextIndex to {}", peerId, nextIndex.get(peerId));
            }
        }
    }
    private void advanceCommitIndex() {
        // 从当前的 commitIndex + 1 开始，尝试找到一个可以被提交的 index
        for (long N = commitIndex + 1; N <= logModule.getLastIndex(); N++) {
            // 检查该日志的任期是否是当前任期
            LogEntry entry = logModule.getEntry(N);
            if (entry != null && entry.getTerm() == this.currentTerm) {
                int matchCount = 1; // 1 是 Leader 自己
                for (String peerId : peerAddresses.keySet()) {
                    if (matchIndex.get(peerId) >= N) {
                        matchCount++;
                    }
                }

                // 如果超过半数的节点已经复制了该日志，则提交它
                if (matchCount > (peerAddresses.size() + 1) / 2) {
                    commitIndex = N;
                    logger.info("Commit index advanced to {}", commitIndex);
                    // TODO: 应用日志到状态机 (apply to state machine)
                } else {
                    // 由于日志是顺序匹配的，如果 N 不能被提交，那么 N 之后的所有日志也不能
                    break;
                }
            }
        }
    }
    /**
     * 处理来自客户端的请求，只有 Leader 能处理
     * @param command 客户端命令
     * @return 是否成功接收（不代表成功执行）
     */
    public ClientResponse handleClientRequest(Command command) {
        if (state != NodeState.LEADER) {
            logger.warn("Node {} is not a leader, redirecting client.", selfId);
            String leaderAddress = peerAddresses.get(currentLeaderId);
            // 增加一个检查，防止因为刚启动currentLeaderId还未同步而返回null
            if (leaderAddress == null) {
                leaderAddress = "";
            }
            // **关键修改：返回失败响应，并附上 Leader 的地址**
            return ClientResponse.failure(currentLeaderId, peerAddresses.get(currentLeaderId));
        }
        if ("GET".equalsIgnoreCase(command.getOperation())) {
            String value = stateMachine.get(command.getKey());
            return ClientResponse.success(value);
        }
        logger.info("Leader {} received command: {}", selfId, command);

        // 1. 将命令作为新条目追加到自己的日志中
        long newIndex = logModule.getLastIndex() + 1;
        LogEntry newEntry = new LogEntry(this.currentTerm, newIndex, command);
        logModule.append(newEntry);
        logger.info("Leader {} appended new entry at index {}", selfId, newIndex);

        // 2. 向所有 Follower 并发地发送 AppendEntries RPC 来复制该条目
        replicateLogsToFollowers();

        return ClientResponse.success("OK");
    }
    /**
     * 比较候选人的日志是否至少和本节点一样新
     * Raft 论文 5.4.1 节
     */
    private boolean isLogUpToDate(long candidateLastLogTerm, long candidateLastLogIndex) {
        LogEntry selfLastEntry = logModule.getLastEntry();
        if (selfLastEntry == null) {
            return true; // 如果自己没有日志，任何候选人的日志都算新的
        }

        if (candidateLastLogTerm > selfLastEntry.getTerm()) {
            return true;
        }
        if (candidateLastLogTerm == selfLastEntry.getTerm() && candidateLastLogIndex >= selfLastEntry.getIndex()) {
            return true;
        }
        return false;
    }
    // ***** NEW *****: 加载和保存状态的方法
    private void loadPersistentState() {
        logger.info("Node {} loading persistent state...", selfId);
        PersistentState state = FileStorage.load(metadataFile);
        this.currentTerm = state.getCurrentTerm();
        this.votedFor = state.getVotedFor();
        logger.info("Node {} loaded state: currentTerm={}, votedFor={}", selfId, this.currentTerm, this.votedFor);
    }

    private synchronized void persistState() {
        try {
            PersistentState state = new PersistentState(this.currentTerm, this.votedFor);
            FileStorage.save(state, metadataFile);
        } catch (IOException e) {
            logger.error("FATAL: Failed to persist state on node {}. Shutting down to prevent inconsistency.", selfId, e);
            // 在生产环境中，这种失败是致命的，可能需要关闭节点
            System.exit(1);
        }
    }
    // 辅助方法，用于 RpcServerHandler
    public String getSelfId() {
        return selfId;
    }
}
