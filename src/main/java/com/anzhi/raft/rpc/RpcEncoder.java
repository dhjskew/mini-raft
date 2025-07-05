package com.anzhi.raft.rpc;


import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;

public class RpcEncoder extends MessageToByteEncoder<RpcMessage> {

    private final JsonSerializer serializer = new JsonSerializer();

    @Override
    protected void encode(ChannelHandlerContext ctx, RpcMessage msg, ByteBuf out) throws Exception {
        // 1. 将 RpcMessage 对象序列化成 byte[]
        byte[] data = serializer.serialize(msg);

        // 2. 将消息的长度写入 ByteBuf，作为一个4字节的整数
        out.writeInt(data.length);

        // 3. 写入消息体本身
        out.writeBytes(data);
    }
}
