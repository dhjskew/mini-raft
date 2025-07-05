package com.anzhi.raft.rpc;


import com.anzhi.raft.Node;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CountDownLatch;

public class RpcServer {
    private static final Logger logger = LoggerFactory.getLogger(RpcServer.class);
    private final int port;
    private final Node node; // 持有一个 Node 实例的引用
    private final CountDownLatch latch; // 新增 latch


    public RpcServer(int port, Node node,CountDownLatch latch) { // 构造函数接收 Node 实例
        this.port = port;
        this.node = node;
        this.latch = latch;
    }



    public void start() throws Exception {
        EventLoopGroup bossGroup = new NioEventLoopGroup(1);
        EventLoopGroup workerGroup = new NioEventLoopGroup();

        try {
            ServerBootstrap b = new ServerBootstrap();
            b.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    // TODO: 替换为实际的 Raft 业务处理器
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        public void initChannel(SocketChannel ch) throws Exception {
                            ch.pipeline().addLast(new RpcDecoder());
                            ch.pipeline().addLast(new RpcEncoder());
                            // 在这里添加你的业务逻辑 Handler
                            ch.pipeline().addLast(new RpcServerHandler(node));
                        }
                    })
                    .option(ChannelOption.SO_BACKLOG, 128)
                    .childOption(ChannelOption.SO_KEEPALIVE, true);

            // 绑定端口并同步等待成功
            ChannelFuture f = b.bind(port).sync();
            logger.info("RPC Server started and listening on port: {}", port);

            // ***** 关键修复 *****
            // 服务器成功启动，latch 减一
            if (latch != null) {
                latch.countDown();
            }

            f.channel().closeFuture().sync();
        } finally {
            workerGroup.shutdownGracefully();
            bossGroup.shutdownGracefully();
        }
    }
}
