package com.anzhi.raft.rpc;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class RpcClient {
    private static final Logger logger = LoggerFactory.getLogger(RpcClient.class);

    // ***** 关键修改 1: 单例模式 *****
    private static final RpcClient INSTANCE = new RpcClient();
    private final EventLoopGroup group;
    private final Bootstrap bootstrap;
    private final ConcurrentHashMap<String, Channel> channels = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CompletableFuture<Object>> pendingRequests = new ConcurrentHashMap<>();

    // 私有构造函数
    private RpcClient() {
        this.group = new NioEventLoopGroup();
        this.bootstrap = new Bootstrap();
        this.bootstrap.group(group)
                .channel(NioSocketChannel.class)
                .option(ChannelOption.TCP_NODELAY, true)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    public void initChannel(SocketChannel ch) throws Exception {
                        ch.pipeline().addLast(new RpcDecoder());
                        ch.pipeline().addLast(new RpcEncoder());
                        ch.pipeline().addLast(new RpcClientHandler());
                    }
                });
    }

    public static RpcClient getInstance() {
        return INSTANCE;
    }

    // ***** 关键修改 2: 完全异步的 sendRequest 方法 *****
    public CompletableFuture<Object> sendRequest(String host, int port, Object payload) {
        String address = host + ":" + port;
        CompletableFuture<Object> future = new CompletableFuture<>();

        // 创建 RPC 消息
        RpcMessage message = new RpcMessage();
        message.setRequestId(UUID.randomUUID().toString());
        message.setMessageType(payload.getClass().getSimpleName());
        message.setPayload(payload);

        // 尝试从缓存获取 Channel
        Channel channel = channels.get(address);

        // 如果 Channel 不存在或已失效，则建立新连接
        if (channel == null || !channel.isActive()) {
            connectAndSend(address, host, port, message, future);
        } else {
            // Channel 存在且活跃，直接发送
            writeAndFlush(channel, message, future);
        }

        return future;
    }

    private void connectAndSend(String address, String host, int port, RpcMessage message, CompletableFuture<Object> future) {
        // 使用 addListener 实现异步连接
        bootstrap.connect(host, port).addListener((ChannelFuture connectFuture) -> {
            if (connectFuture.isSuccess()) {
                // 连接成功
                logger.debug("Successfully connected to {}", address);
                Channel newChannel = connectFuture.channel();
                channels.put(address, newChannel);
                // **在连接成功的回调中**发送数据，消除了竞态条件
                writeAndFlush(newChannel, message, future);
            } else {
                // 连接失败
                logger.error("Failed to connect to {}", address, connectFuture.cause());
                future.completeExceptionally(connectFuture.cause());
            }
        });
    }

    private void writeAndFlush(Channel channel, RpcMessage message, CompletableFuture<Object> future) {
        // 将 future 放入等待 map
        pendingRequests.put(message.getRequestId(), future);

        // 发送数据，并添加监听器处理发送失败的情况
        channel.writeAndFlush(message).addListener((ChannelFuture writeFuture) -> {
            if (!writeFuture.isSuccess()) {
                logger.error("Failed to write to channel {}", channel.remoteAddress(), writeFuture.cause());
                // 从等待 map 中移除并设置异常
                pendingRequests.remove(message.getRequestId());
                future.completeExceptionally(writeFuture.cause());
            }
        });
    }

    public void shutdown() {
        logger.info("Shutting down RpcClient...");
        group.shutdownGracefully();
    }

    // 内部 Handler，用于处理响应
    private class RpcClientHandler extends SimpleChannelInboundHandler<RpcMessage> {
        @Override
        protected void channelRead0(ChannelHandlerContext ctx, RpcMessage msg) throws Exception {
            CompletableFuture<Object> future = pendingRequests.remove(msg.getRequestId());
            if (future != null) {
                // 响应成功，完成 Future
                future.complete(msg.getPayload());
            } else {
                logger.warn("Received response for unknown request id: {}", msg.getRequestId());
            }
        }
    }
}
