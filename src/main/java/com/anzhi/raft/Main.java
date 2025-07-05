package com.anzhi.raft;

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
        Path baseDataDir = Paths.get("./data");
        // 正式运行时可以注释掉数据清理
        if (Files.exists(baseDataDir)) {
            Files.walk(baseDataDir)
                    .sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
        }
        logger.info("Cleaned up old data directories.");

        Map<String, String> clusterConfig = new HashMap<>();
        clusterConfig.put("node-1", "localhost:8081");
        clusterConfig.put("node-2", "localhost:8082");
        clusterConfig.put("node-3", "localhost:8083");

        int nodeCount = clusterConfig.size();
        final CountDownLatch serverReadyLatch = new CountDownLatch(nodeCount);

        List<Node> nodes = new ArrayList<>();
        List<Thread> serverThreads = new ArrayList<>();

        logger.info("Initializing {} nodes...", nodeCount);
        for (String nodeId : clusterConfig.keySet()) {
            Map<String, String> peerConfig = new HashMap<>(clusterConfig);
            peerConfig.remove(nodeId);

            Path nodeDataDir = baseDataDir.resolve(nodeId);
            Node node = new Node(nodeId, peerConfig, nodeDataDir);
            nodes.add(node);

            String address = clusterConfig.get(nodeId);
            int port = Integer.parseInt(address.split(":")[1]);
            RpcServer rpcServer = new RpcServer(port, node, serverReadyLatch);

            Thread serverThread = new Thread(() -> {
                try {
                    rpcServer.start();
                } catch (Exception e) {
                    logger.error("RPC Server for node {} failed to start", nodeId, e);
                }
            });
            serverThread.setName("RpcServerThread-" + nodeId);
            serverThreads.add(serverThread);
        }

        logger.info("Starting all RPC servers...");
        for (Thread t : serverThreads) {
            t.start();
        }

        logger.info("Main thread is waiting for all RPC servers to become ready...");
        serverReadyLatch.await();
        logger.info("All RPC servers are ready!");

        logger.info("Starting all Raft nodes' core logic...");
        for (Node node : nodes) {
            node.start();
        }

        // 添加一个关闭钩子，在按 Ctrl+C 等操作时执行
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Shutdown hook triggered. Shutting down all nodes and clients...");
            for (Node node : nodes) {
                node.shutdown();
            }
            logger.info("All nodes shut down.");
        }));

        // 服务器启动器的主线程在这里可以永远等待，或者做一些监控工作
        // 我们让它在这里永远阻塞，这样 JVM 就不会退出
        Thread.currentThread().join();
    }
}
