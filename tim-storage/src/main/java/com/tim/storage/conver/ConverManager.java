package com.tim.storage.conver;

import static com.tim.common.utils.Constants.PREFIX_CONVERSATION_LIST;
import static com.tim.common.utils.Constants.PREFIX_MESSAGE_ID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.tim.common.model.ConverInfo;
import com.tim.common.model.ConverListInfo;
import com.tim.common.model.MsgContent;
import com.tim.common.protos.Message.ConverType;
import com.tim.common.protos.Message.MessageContent;
import com.tim.common.utils.JsonHelper;
import com.tim.common.utils.UidUtil;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.RedisZSetCommands.Limit;
import org.springframework.data.redis.connection.RedisZSetCommands.Range;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.util.CollectionUtils;

@Slf4j
public class ConverManager {

    private RedisTemplate redisTemplate;

    public ConverManager(RedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    private ConverManager() {
    }

    public String newSingleConverId(String fromUid, String toUid) {
        String converId = UidUtil.uuid24By2Factor(fromUid, toUid);
        Set<String> uidList = new HashSet<>();
        uidList.add(fromUid);
        uidList.add(toUid);
        ConverInfo converInfo = new ConverInfo().setId(converId)
            .setType(ConverType.SINGLE.getNumber())
            .setUidList(CollectionUtils.arrayToList(uidList.toArray()));
        try {
            if (redisTemplate.opsForValue()
                .setIfAbsent(converId, JsonHelper.toJsonString(converInfo))) {
                redisTemplate.boundHashOps(PREFIX_CONVERSATION_LIST + fromUid).put(converId, 0);
                redisTemplate.boundHashOps(PREFIX_CONVERSATION_LIST + toUid).put(converId, 0);
            }
        } catch (JsonProcessingException e) {
            log.error("json processing error", e);
        }
        return converId;
    }

    public String newGroupConverId(String groupId, List<String> members) {
        String converId = UidUtil.uuid24ByFactor(groupId);
        ConverInfo converInfo = new ConverInfo().setId(converId)
            .setType(ConverType.GROUP.getNumber())
            .setUidList(members).setGroupId(groupId);
        try {
            if (redisTemplate.opsForValue()
                .setIfAbsent(converId, JsonHelper.toJsonString(converInfo))) {
                members
                    .forEach(member -> redisTemplate.boundHashOps(PREFIX_CONVERSATION_LIST + member)
                        .put(converId, 0));
            }
        } catch (JsonProcessingException e) {
            log.error("json processing error", e);
        }
        return converId;
    }

    public void addMemberConverList(String groupId, List<String> members) {
        String converId = UidUtil.uuid24ByFactor(groupId);
        ConverInfo converInfo = getConverInfo(converId);
        List<String> memberList = converInfo.getUidList();
        memberList.addAll(members);
        converInfo.setUidList(memberList);
        try {
            redisTemplate.opsForValue().set(converId, JsonHelper.toJsonString(converInfo));
        } catch (JsonProcessingException e) {
            log.error("json processing error", e);
        }

        members.forEach(member -> redisTemplate.boundHashOps(PREFIX_CONVERSATION_LIST + member)
            .put(converId, 0));
    }

    public void removeMemberConverList(String groupId, List<String> members) {
        String converId = UidUtil.uuid24ByFactor(groupId);
        ConverInfo converInfo = getConverInfo(converId);
        List<String> memberList = converInfo.getUidList();
        memberList.removeAll(members);
        converInfo.setUidList(memberList);
        try {
            redisTemplate.opsForValue().set(converId, JsonHelper.toJsonString(converInfo));
        } catch (JsonProcessingException e) {
            log.error("json processing error", e);
        }
        members.forEach(member -> redisTemplate.boundHashOps(PREFIX_CONVERSATION_LIST + member)
            .delete(converId));
    }

    public void removeConversation(String groupId, List<String> members) {
        String converId = UidUtil.uuid24ByFactor(groupId);
        redisTemplate.delete(converId);
        removeMemberConverList(groupId, members);
    }

    public boolean isSingleConverIdValid(String converId) {
        ConverInfo converInfo = getConverInfo(converId);
        return converInfo == null ? false : converInfo.getType() == ConverType.SINGLE.getNumber();
    }

    public boolean isGroupConverIdValid(String converId) {
        ConverInfo converInfo = getConverInfo(converId);
        return converInfo == null ? false : converInfo.getType() == ConverType.GROUP.getNumber();
    }

    public void cacheMsg2Conver(MessageContent msg, String converId) throws Exception {
        MsgContent msgContent = new MsgContent().setId(msg.getId()).setUid(msg.getUid())
            .setType(msg.getType().getNumber()).setContent(msg.getContent()).setTime(msg.getTime());
        String str = JsonHelper.toJsonString(msgContent);
        redisTemplate.boundZSetOps(PREFIX_MESSAGE_ID + converId).add(str, msgContent.getTime());
    }

    public List<MsgContent> getHistoryMsg(String converId, Long beginTime) {
        List<MsgContent> msgContents = new ArrayList<>();
        long now = System.currentTimeMillis();
        Set<String> messages = redisTemplate.opsForZSet()
            .rangeByScore(PREFIX_MESSAGE_ID + converId, Double.valueOf(beginTime),
                Double.valueOf(now), 0, 100);
        for (String message : messages) {
            MsgContent msgContent = JsonHelper
                .readValue(message, MsgContent.class);
            msgContents.add(msgContent);
        }
        return msgContents;
    }

    public ConverInfo getConverInfo(String converId) {
        Object ob = redisTemplate.opsForValue().get(converId);
        if (null == ob) {
            return null;
        }
        return JsonHelper.readValue(ob.toString(), ConverInfo.class);
    }

    public List<ConverListInfo> getConverListByUid(String uid) {
        List<ConverListInfo> list = new ArrayList<>();
        Map<String, Integer> converList = redisTemplate.boundHashOps(PREFIX_CONVERSATION_LIST + uid)
            .entries();
        for (Entry<String, Integer> entry : converList.entrySet()) {
            ConverInfo converInfo = getConverInfo(entry.getKey());
            if (null != converInfo) {
                Set<String> strs = redisTemplate
                    .boundZSetOps(PREFIX_MESSAGE_ID + converInfo.getId())
                    .range(-1, -1);
                if (strs.size() >= 1) {
                    MsgContent msgContent = JsonHelper
                        .readValue(strs.iterator().next().toString(), MsgContent.class);
                    ConverListInfo converListInfo = new ConverListInfo()
                        .setId(converInfo.getId()).setGroupId(converInfo.getGroupId())
                        .setLastContent(msgContent)
                        .setUidList(converInfo.getUidList()).setType(converInfo.getType())
                        .setUnCount(entry.getValue());
                    list.add(converListInfo);
                }
            }

        }
        return list;
    }

    public List<String> getUidListByConver(String converId) {
        ConverInfo converInfo = getConverInfo(converId);
        if (converInfo.getType() == ConverType.SINGLE.getNumber()
            || converInfo.getType() == ConverType.GROUP.getNumber()) {
            return converInfo.getUidList();
        }
        return new ArrayList<>();
    }

    public List<String> getUidListByConverExcludeSender(String converId, String fromUser) {
        ConverInfo converInfo = getConverInfo(converId);
        if (converInfo.getType() == ConverType.SINGLE.getNumber() ||
            converInfo.getType() == ConverType.GROUP.getNumber()) {
            List<String> uids = converInfo.getUidList();
            uids.remove(fromUser);
            return uids;
        }
        return new ArrayList<>();
    }

    public void incrUserConverUnCount(String uid, String converId, int num) {
        redisTemplate.boundHashOps(PREFIX_CONVERSATION_LIST + uid).increment(converId, num);
    }

}
