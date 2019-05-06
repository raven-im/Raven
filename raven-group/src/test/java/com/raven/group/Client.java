package com.raven.group;

import com.raven.common.protos.Message;
import com.raven.common.protos.Message.RavenMessage;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.protobuf.ProtobufDecoder;
import io.netty.handler.codec.protobuf.ProtobufEncoder;
import io.netty.handler.codec.protobuf.ProtobufVarint32FrameDecoder;
import io.netty.handler.codec.protobuf.ProtobufVarint32LengthFieldPrepender;
import lombok.extern.slf4j.Slf4j;

/**
 * Author zxx Description Simple client for module test Date Created on 2018/5/25
 */
@Slf4j
public class Client {

    private static final String HOST = "127.0.0.1";
    private static final int PORT = 7370;
//    public static SnowFlake snowFlake = new SnowFlake(1, 2);

    private static void baseTest(SimpleChannelInboundHandler<RavenMessage> handler) throws InterruptedException {
        EventLoopGroup group = new NioEventLoopGroup();
        Bootstrap b = new Bootstrap();
        b.group(group)
            .channel(NioSocketChannel.class)
            .option(ChannelOption.TCP_NODELAY, true)
            .handler(new ChannelInitializer<SocketChannel>() {
                @Override
                public void initChannel(SocketChannel ch) throws Exception {
                ch.pipeline()
                    .addLast(new ProtobufVarint32FrameDecoder())// 处理半包消息的解码类
                    .addLast(new ProtobufDecoder(Message.RavenMessage.getDefaultInstance()))
                    .addLast(new ProtobufVarint32LengthFieldPrepender())// 对protobuf协议的消息头上加上一个长度为32的整形字段
                    .addLast(new ProtobufEncoder())
                    .addLast(handler);
                }
            });
        startConnection(b);
    }

    private static void startConnection(Bootstrap b) {
        b.connect(HOST, PORT).addListener(future -> {
            if (future.isSuccess()) {
                //init registry
                log.info("Client connected GroupTcpServer Success...");
            } else {
                log.error("Client connected GroupTcpServer Failed");
            }
        });
    }

    public static void sendGroupMsgTest(RavenMessage msg, GroupListener listener) throws InterruptedException {
        baseTest(new GroupMsgHandler(msg, listener));
    }
}
