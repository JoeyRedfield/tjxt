package com.tianji.remark.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.api.dto.msg.LikedTimesDTO;
import com.tianji.common.autoconfigure.mq.RabbitMqHelper;
import com.tianji.common.constants.MqConstants;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.StringUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.remark.constants.RedisConstants;
import com.tianji.remark.domain.dto.LikeRecordFormDTO;
import com.tianji.remark.domain.po.LikedRecord;
import com.tianji.remark.mapper.LikedRecordMapper;
import com.tianji.remark.service.ILikedRecordService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.cache.RedisCache;
import org.springframework.data.redis.connection.StringRedisConnection;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * <p>
 * 点赞记录表 服务实现类
 * </p>
 *
 * @author zywu
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class LikedRecordRedisServiceImpl extends ServiceImpl<LikedRecordMapper, LikedRecord> implements ILikedRecordService {

    private final RabbitMqHelper rabbitMqHelper;
    private final StringRedisTemplate redisTemplate; // 等同于RedisTemplate<String, String>

    @Transactional
    @Override
    public void addLikeRecord(LikeRecordFormDTO dto) {
        // 查出用户id
        Long userId = UserContext.getUser();
        // 判断是点赞还是取消赞
        boolean flag = dto.getLiked() ? liked(dto, userId) : unliked(dto, userId);
        if(!flag){
            // 操作没成功
            return;
        }
        // 成功就统计点赞总数
        String likeBizKey = RedisConstants.LIKE_BIZ_KEY_PREFIX + dto.getBizId();
        Long totalLikesNum = redisTemplate.opsForSet().size(likeBizKey);
        if(totalLikesNum == null){
            return;
        }

        String BizTypeKey = RedisConstants.LIKE_COUNT_KEY_PREFIX + dto.getBizType();
        redisTemplate.opsForZSet().add(BizTypeKey, dto.getBizId().toString(), totalLikesNum);

    }

    @Override
    public Set<Long> getLikesStatusByBizIds(List<Long> bizIds) {
        if(CollUtils.isEmpty(bizIds)){
            return CollUtils.emptySet();
        }
        Long userId = UserContext.getUser();
        Set<Long> retSet = new HashSet<>();
        for (Long bizId : bizIds) {
            String bizIdKeys = RedisConstants.LIKE_BIZ_KEY_PREFIX + bizId;
            Boolean member = redisTemplate.opsForSet().isMember(bizIdKeys, userId.toString());
            if(member){
                retSet.add(bizId);
            }
        }
        return retSet;

        /*// 1.获取登录用户id
        Long userId = UserContext.getUser();
        // 2.查询点赞状态
        List<Object> objects = redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            StringRedisConnection src = (StringRedisConnection) connection;
            for (Long bizId : bizIds) {
                String key = RedisConstants.LIKE_BIZ_KEY_PREFIX + bizId;
                src.sIsMember(key, userId.toString());
            }
            return null;
        });
        // 3.返回结果
        return IntStream.range(0, objects.size()) // 创建从0到集合size的流
                .filter(i -> (boolean) objects.get(i)) // 遍历每个元素，保留结果为true的角标i
                .mapToObj(bizIds::get)// 用角标i取bizIds中的对应数据，就是点赞过的id
                .collect(Collectors.toSet());// 收集*/

    }

    @Override
    public void readLikedTimesAndSendMessage(String bizType, int maxBizSize) {
        // 拼接key
        String bizTypeKey = RedisConstants.LIKE_COUNT_KEY_PREFIX + bizType;
        Set<ZSetOperations.TypedTuple<String>> typedTuples = redisTemplate.opsForZSet().popMin(bizTypeKey, maxBizSize);
        if(typedTuples == null){
            return;
        }

        List<LikedTimesDTO> list = new ArrayList<>();
        for (ZSetOperations.TypedTuple<String> typedTuple : typedTuples) {
            Double times = typedTuple.getScore();
            String bizId = typedTuple.getValue();
            if(StringUtils.isBlank(bizId) || times==null){
                continue;
            }
            LikedTimesDTO dto = new LikedTimesDTO();

            dto.setBizId(Long.valueOf(bizId));
            dto.setLikedTimes(times.intValue());
            list.add(dto);
        }
        String routingKey = StringUtils.format(MqConstants.Key.LIKED_TIMES_KEY_TEMPLATE, bizType);
        if(CollUtils.isNotEmpty(list)){
            rabbitMqHelper.send(
                    MqConstants.Exchange.LIKE_RECORD_EXCHANGE,
                    routingKey,
                    list);
        }
    }

    private boolean unliked(LikeRecordFormDTO dto, Long userId) {
        String key = RedisConstants.LIKE_BIZ_KEY_PREFIX + dto.getBizId();
        Long res = redisTemplate.opsForSet().remove(key, userId.toString());
        return res != null && res > 0; // 条件成立说明添加成功.
    }

    // 点赞
    private boolean liked(LikeRecordFormDTO dto, Long userId) {
        //基于redis点赞
        //拼接key
        String key = RedisConstants.LIKE_BIZ_KEY_PREFIX + dto.getBizId();
        Long res = redisTemplate.opsForSet().add(key, userId.toString());
        return res != null && res > 0; // 条件成立说明添加成功.
    }
}
