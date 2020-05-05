package com.raven.gateway.handler;

import com.raven.common.model.Conversation;
import com.raven.common.model.MsgContent;
import com.raven.common.protos.Message.*;
import com.raven.common.protos.Message.RavenMessage.Type;
import com.raven.common.utils.JsonHelper;
import com.raven.storage.conver.ConverManager;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@Sharable
@Slf4j
public class HistoryHandler extends SimpleChannelInboundHandler<RavenMessage> {

    @Autowired
    private ConverManager converManager;

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, RavenMessage message) throws Exception {
        if (message.getType() == Type.HisMessagesReq) {
            HisMessagesReq req = message.getHisMessagesReq();
            log.info("receive history message request:{}", JsonHelper.toJsonString(req));
            Conversation conversation = converManager.getConversation(req.getConverId());
            List<MsgContent> msgContents = converManager
                    .getHistoryMsg(req.getConverId(), req.getBeginId());
            List<MessageContent> contentList = new ArrayList<>();
            for (MsgContent msgContent : msgContents) {
                MessageContent content = MessageContent.newBuilder()
                        .setContent(msgContent.getContent())
                        .setTime(msgContent.getTimestamp())
                        .setType(msgContent.getType())
                        .build();
                contentList.add(content);
            }
            Long unReadCount = converManager
                    .getHistoryUnReadCount(req.getConverId(), req.getBeginId());
            HisMessagesAck hisMessagesAck = HisMessagesAck.newBuilder().setId(req.getId())
                    .setConverId(req.getConverId()).setUnReadCount(unReadCount)
                    .setConvType(conversation.getType() == ConverType.GROUP.getNumber() ? ConverType.GROUP : ConverType.SINGLE)
                    .addAllMessageList(contentList).build();
            RavenMessage ravenMessage = RavenMessage.newBuilder().setType(Type.HisMessagesAck)
                    .setHisMessagesAck(hisMessagesAck).build();
            ctx.writeAndFlush(ravenMessage);
        } else {
            ctx.fireChannelRead(message);
        }
    }

}
