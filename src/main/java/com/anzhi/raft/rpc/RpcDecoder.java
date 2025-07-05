package com.anzhi.raft.rpc;


import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;

import java.util.List;

public class RpcDecoder extends ByteToMessageDecoder {

    private final JsonSerializer serializer = new JsonSerializer();

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        // 检查是否至少有4个字节可读 (用于读取消息长度)
        if (in.readableBytes() < 4) {
            return;
        }

        // 标记当前的读索引
        in.markReaderIndex();

        int dataLength = in.readInt();

        // 如果剩余的可读字节数小于消息长度，说明消息不完整，重置读索引并等待
        if (in.readableBytes() < dataLength) {
            in.resetReaderIndex();
            return;
        }

        // 读取消息体
        byte[] data = new byte[dataLength];
        in.readBytes(data);

        // 反序列化
        RpcMessage message = serializer.deserialize(data, RpcMessage.class);
        out.add(message);
    }
}
