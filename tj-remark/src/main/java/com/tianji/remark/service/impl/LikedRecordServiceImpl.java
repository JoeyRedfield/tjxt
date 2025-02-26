//package com.tianji.remark.service.impl;
//
//import com.tianji.api.dto.msg.LikedTimesDTO;
//import com.tianji.common.autoconfigure.mq.RabbitMqHelper;
//import com.tianji.common.constants.MqConstants;
//import com.tianji.common.utils.CollUtils;
//import com.tianji.common.utils.StringUtils;
//import com.tianji.common.utils.UserContext;
//import com.tianji.remark.domain.dto.LikeRecordFormDTO;
//import com.tianji.remark.domain.po.LikedRecord;
//import com.tianji.remark.mapper.LikedRecordMapper;
//import com.tianji.remark.service.ILikedRecordService;
//import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.stereotype.Service;
//import org.springframework.transaction.annotation.Transactional;
//
//import java.util.List;
//import java.util.Set;
//import java.util.stream.Collectors;
//
///**
// * <p>
// * 点赞记录表 服务实现类
// * </p>
// *
// * @author zywu
// */
//@Slf4j
//@RequiredArgsConstructor
//@Service
//public class LikedRecordServiceImpl extends ServiceImpl<LikedRecordMapper, LikedRecord> implements ILikedRecordService {
//
//    private final RabbitMqHelper rabbitMqHelper;
//
//    @Transactional
//    @Override
//    public void addLikeRecord(LikeRecordFormDTO dto) {
//        // 查出用户id
//        Long userId = UserContext.getUser();
//        // 判断是点赞还是取消赞
//        boolean flag = dto.getLiked() ? liked(dto, userId) : unliked(dto, userId);
//        if(!flag){
//            // 操作没成功
//            return;
//        }
//        // 统计该业务id总的点赞数量
//        Integer count = lambdaQuery()
//                .eq(LikedRecord::getBizId, dto.getBizId())
//                .count();
//        // 通知MQ去更新数据
//        LikedTimesDTO likedTimesDTO = LikedTimesDTO.of(dto.getBizId(), count);
//        String routingKey = StringUtils.format(MqConstants.Key.LIKED_TIMES_KEY_TEMPLATE, dto.getBizType());
//
//        log.debug("发送点赞消息, 消息内容: {}", likedTimesDTO);
//        rabbitMqHelper.send(
//                MqConstants.Exchange.LIKE_RECORD_EXCHANGE,
//                routingKey,
//                likedTimesDTO
//        );
//    }
//
//    @Override
//    public Set<Long> getLikesStatusByBizIds(List<Long> bizIds) {
//        if(CollUtils.isEmpty(bizIds)){
//            return CollUtils.emptySet();
//        }
//        List<LikedRecord> list = lambdaQuery()
//                .eq(LikedRecord::getUserId, UserContext.getUser())
//                .in(LikedRecord::getBizId, bizIds).list();
//
//        return list.stream().map(LikedRecord::getBizId).collect(Collectors.toSet());
//    }
//
//    private boolean unliked(LikeRecordFormDTO dto, Long userId) {
//        // 根据bizId和userId, 查记录.
//        LikedRecord one = lambdaQuery()
//                .eq(LikedRecord::getBizId, dto.getBizId())
//                .eq(LikedRecord::getUserId, userId)
//                .one();
//        if(one == null){
//            // 如果没有这条记录, 说明也没有取消的逻辑
//            return false;
//        }
//        // 如果查到, 那就删除记录, 返回结果
//        return removeById(one);
//    }
//
//    // 点赞
//    private boolean liked(LikeRecordFormDTO dto, Long userId) {
//        // 根据bizId和userId, 查记录.
//        LikedRecord one = lambdaQuery()
//                .eq(LikedRecord::getBizId, dto.getBizId())
//                .eq(LikedRecord::getUserId, userId)
//                .one();
//        if(one != null){
//            // 如果有这条记录, 说明是重复点赞, 返回false
//            return false;
//        }
//        // 如果没查到, 那就添加记录, 返回true
//        LikedRecord likedRecord = new LikedRecord();
//        likedRecord.setBizId(dto.getBizId());
//        likedRecord.setBizType(dto.getBizType());
//        likedRecord.setUserId(userId);
//
//        return save(likedRecord);
//    }
//}
