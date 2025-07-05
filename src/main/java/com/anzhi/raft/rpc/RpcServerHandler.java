package com.anzhi.raft.rpc;

import com.anzhi.raft.Node;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RpcServerHandler extends SimpleChannelInboundHandler<RpcMessage> {
    private static final Logger logger = LoggerFactory.getLogger(RpcServerHandler.class);
    private final Node node;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public RpcServerHandler(Node node) {
        this.node = node;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, RpcMessage msg) throws Exception {
        // 探针 1: 检查消息是否进入了 Handler
//        logger.info("<<<<< RpcServerHandler received a message! Type: {}, RequestId: {}", msg.getMessageType(), msg.getRequestId());

        Object resultPayload = null;
        switch (msg.getMessageType()) {
            case "RequestVoteArgs":
//                logger.info("<<<<< Matched RequestVoteArgs case."); // 保留探针
                RequestVoteArgs voteArgs = (RequestVoteArgs) msg.getPayload(); // 直接转换
                resultPayload = node.handleRequestVote(voteArgs);
                break;
            case "AppendEntriesArgs":
//                logger.info("<<<<< Matched AppendEntriesArgs case."); // 保留探针
                AppendEntriesArgs appendArgs = (AppendEntriesArgs) msg.getPayload(); // 直接转换
                resultPayload = node.handleAppendEntries(appendArgs);
                break;
            default:
                logger.warn("Unknown message type: {}", msg.getMessageType());
        }

        if (resultPayload != null) {
            // 创建响应消息
            RpcMessage response = new RpcMessage();
            response.setRequestId(msg.getRequestId()); // 响应必须使用相同的 requestId
            response.setMessageType(resultPayload.getClass().getSimpleName());
            response.setPayload(resultPayload);

            ctx.writeAndFlush(response);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        logger.error("Exception caught in RPC handler", cause);
        ctx.close();
    }
}
