package com.anzhi.raft.client;

import com.anzhi.raft.core.Command;
import com.anzhi.raft.rpc.RpcClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Scanner;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ThreadLocalRandom;

public class RaftClient {
    private static final Logger logger = LoggerFactory.getLogger(RaftClient.class);

    private final RpcClient rpcClient = RpcClient.getInstance();
    private final List<String> peerAddresses;
    private String currentLeaderAddress;

    public RaftClient(List<String> peerAddresses) {
        this.peerAddresses = peerAddresses;
        this.currentLeaderAddress = peerAddresses.get(ThreadLocalRandom.current().nextInt(peerAddresses.size()));
    }

    /**
     * 发送命令并处理重定向和重试的核心逻辑
     * @param command 要发送的命令
     * @return 服务器的响应
     */
    public ClientResponse send(Command command) {
        int maxRetries = 5;
        for (int i = 0; i < maxRetries; i++) {
            logger.debug("Attempt {} to send command to {}", i + 1, currentLeaderAddress);

            String[] parts = currentLeaderAddress.split(":");
            String host = parts[0];
            int port = Integer.parseInt(parts[1]);

            ClientRequest request = new ClientRequest(command);
            CompletableFuture<Object> future = rpcClient.sendRequest(host, port, request);

            try {
                ClientResponse response = (ClientResponse) future.get(); // 阻塞等待响应
                if (response.isSuccess()) {
                    return response; // 成功，直接返回响应
                } else {
                    // 如果失败，并且是重定向响应
                    if (response.getLeaderAddress() != null && !response.getLeaderAddress().isEmpty()) {
                        currentLeaderAddress = response.getLeaderAddress();
                        logger.warn("Not a leader, redirecting to new leader: {}", currentLeaderAddress);
                    } else {
                        // 如果没有 leader 信息，可能是选举中，随机换一个节点重试
                        currentLeaderAddress = peerAddresses.get(ThreadLocalRandom.current().nextInt(peerAddresses.size()));
                        logger.warn("No leader info, trying another random node: {}", currentLeaderAddress);
                    }
                }
            } catch (InterruptedException | ExecutionException e) {
                logger.error("RPC failed for address {}, trying another random node.", currentLeaderAddress, e);
                currentLeaderAddress = peerAddresses.get(ThreadLocalRandom.current().nextInt(peerAddresses.size()));
            }

            // 等待一小段时间再重试
            try {
                Thread.sleep(100 * (i + 1));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
        }
        logger.error("Failed to send command after {} retries.", maxRetries);
        return null;
    }

    /**
     * 启动交互式命令行 Shell
     */
    public void startShell() {
        Scanner scanner = new Scanner(System.in);
        System.out.println("Raft KV Client started. Type 'exit' or 'quit' to close.");

        while (true) {
            System.out.print("raft-cli > ");
            String line = scanner.nextLine().trim();

            if (line.equalsIgnoreCase("exit") || line.equalsIgnoreCase("quit")) {
                break;
            }

            if (line.isEmpty()) {
                continue;
            }

            String[] parts = line.split("\\s+");
            if (parts.length < 2) {
                System.out.println("Invalid command. Usage: SET <key> <value> | GET <key>");
                continue;
            }

            String operation = parts[0].toUpperCase();
            String key = parts[1];
            Command command = null;

            switch (operation) {
                case "SET":
                    if (parts.length < 3) {
                        System.out.println("Invalid SET command. Usage: SET <key> <value>");
                        continue;
                    }
                    String value = parts[2];
                    command = new Command(operation, key, value);
                    break;
                case "GET":
                    command = new Command(operation, key, null);
                    break;
                default:
                    System.out.println("Unknown command: " + operation);
                    continue;
            }

            ClientResponse response = send(command);

            if (response != null && response.isSuccess()) {
                System.out.println(response.getData());
            } else {
                System.out.println("Error: Command failed to execute.");
            }
        }
        scanner.close();
        System.out.println("Goodbye!");
    }

    public static void main(String[] args) {
        // 从配置文件或硬编码中获取集群地址
        List<String> peerAddresses = List.of(
                "localhost:8081",
                "localhost:8082",
                "localhost:8083"
        );

        RaftClient client = new RaftClient(peerAddresses);
        client.startShell();

        // 关闭 RPC 客户端的资源
        RpcClient.getInstance().shutdown();
    }
}
