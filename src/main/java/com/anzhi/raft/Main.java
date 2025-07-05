package com.anzhi.raft;

import com.anzhi.raft.rpc.RpcClient;
import com.anzhi.raft.rpc.RpcServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.CountDownLatch;

public class Main {

    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) throws InterruptedException, IOException {

        // ***** NEW *****: 清理旧数据以进行全新模拟
        Path baseDataDir = Paths.get("./data");
        if (Files.exists(baseDataDir)) {
            Files.walk(baseDataDir)
                    .sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
        }
        logger.info("Cleaned up old data directories.");
        // 要测试崩溃恢复，请注释掉上面的清理代码，然后再次运行程序

        // 1. 定义集群配置
        Map<String, String> clusterConfig = new HashMap<>();
        clusterConfig.put("node-1", "localhost:8081");
        clusterConfig.put("node-2", "localhost:8082");
        clusterConfig.put("node-3", "localhost:8083");

        int nodeCount = clusterConfig.size();
        final CountDownLatch serverReadyLatch = new CountDownLatch(nodeCount);

        List<Node> nodes = new ArrayList<>();
        List<Thread> serverThreads = new ArrayList<>();

        // ==================== 步骤 1: 初始化所有节点和服务器 ====================
        logger.info("Initializing {} nodes...", nodeCount);
        for (String nodeId : clusterConfig.keySet()) {
            // 准备该节点的 peer 配置 (除自己以外的所有节点)
            Map<String, String> peerConfig = new HashMap<>(clusterConfig);
            peerConfig.remove(nodeId);

            // ***** 关键修复 2: 定义数据目录并传递给 Node 构造函数 *****
            Path nodeDataDir = baseDataDir.resolve(nodeId);

            // 使用新的构造函数创建 Raft 节点实例
            Node node = new Node(nodeId, peerConfig, nodeDataDir);
            nodes.add(node);

            // 创建与该节点绑定的 RPC 服务器
            String address = clusterConfig.get(nodeId);
            int port = Integer.parseInt(address.split(":")[1]);
            RpcServer rpcServer = new RpcServer(port, node, serverReadyLatch);

            // 将服务器的启动逻辑放入一个单独的线程
            Thread serverThread = new Thread( () -> {
                try {
                    rpcServer.start();
                } catch (Exception e) {
                    logger.error("RPC Server for node {} failed to start", nodeId, e);
                }
            });
            serverThread.setName("RpcServerThread-" + nodeId);
            serverThreads.add(serverThread);
        }

        // ==================== 步骤 2: 启动所有 RPC 服务器线程 ====================
        logger.info("Starting all RPC servers...");
        for (Thread t : serverThreads) {
            t.start();
        }

        // ==================== 步骤 3: 等待所有服务器就绪 ====================
        logger.info("Main thread is waiting for all RPC servers to become ready...");
        serverReadyLatch.await(); // 主线程将在此阻塞，直到所有服务器都调用了 countDown()
        logger.info("All RPC servers are ready!");

        // ==================== 步骤 4: 启动所有 Raft 节点的核心逻辑 ====================
        logger.info("Starting all Raft nodes' core logic...");
        for (Node node : nodes) {
            node.start();
        }

        // 等待选举完成
        logger.info("Waiting for election to complete...");
        Thread.sleep(5000); // 等待 5 秒，确保有稳定的 Leader 产生

        // 找到 Leader
        Node leader = null;
        for (Node node : nodes) {
            if (node.isLeader()) {
                leader = node;
                break;
            }
        }

        if (leader != null) {
            logger.info("======================================================");
            logger.info("Found leader: {}. Sending client requests...", leader.getSelfId());
            logger.info("======================================================");

            leader.handleClientRequest(new Command("SET", "x", "10"));
            Thread.sleep(100); // 模拟请求间隔
            leader.handleClientRequest(new Command("SET", "y", "20"));

            // 等待日志复制和应用完成
            logger.info("Waiting for log replication and application...");
            Thread.sleep(2000);

            // ***** 最终验证 *****
            logger.info("======================================================");
            logger.info("Verifying state machines across all nodes...");
            logger.info("======================================================");
            for (Node node : nodes) {
                StateMachine sm = node.getStateMachine();
                String valueX = sm.get("x");
                String valueY = sm.get("y");
                logger.info("Node [{}]: state machine has x = {}, y = {}", node.getSelfId(), valueX, valueY);
            }

        } else {
            logger.error("No leader found after timeout.");
        }

        // 优雅地关闭
        logger.info("Simulation finished. Shutting down...");
        System.exit(0);
    }
}
