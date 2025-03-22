package com.tianji.learning.service.impl;

import com.tianji.common.autoconfigure.mq.RabbitMqHelper;
import com.tianji.common.constants.MqConstants;
import com.tianji.common.exceptions.BizIllegalException;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.constans.RedisConstants;
import com.tianji.learning.domain.vo.SignResultVO;
import com.tianji.learning.mq.msg.SignInMessage;
import com.tianji.learning.service.ISignRecordService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SignRecordServiceImpl implements ISignRecordService {

    private final StringRedisTemplate redisTemplate;
    private final RabbitMqHelper mqHelper;

    @Override
    public SignResultVO addSignRecords() {
        Long userId = UserContext.getUser();
        LocalDate now = LocalDate.now();
        String monthAndYear = DateTimeFormatter.ofPattern(":yyyyMM").format(now);
        String signRecordKey = RedisConstants.SIGN_RECORD_KEY_PREFIX + userId.toString() + monthAndYear;
        int offset = now.getDayOfMonth() - 1; //偏移量
        Boolean setBit = redisTemplate.opsForValue().setBit(signRecordKey, offset, true);
        if(setBit){
            // 说明已经签到过了
            throw new BizIllegalException("不能重复签到");
        }
        // 签到完, 返回SignResultVO, 即连续签到天数和签到得分
        int days = continuousDays(now.getDayOfMonth(), signRecordKey);
        int rewardPoints = 0;
        switch (days){
            case 7:
                rewardPoints = 10; break;
            case 14:
                rewardPoints = 20; break;
            case 28:
                rewardPoints = 40; break;
        }
        // todo 6. 保存积分
        mqHelper.send(MqConstants.Exchange.LEARNING_EXCHANGE,
                MqConstants.Key.SIGN_IN,
                SignInMessage.of(userId, rewardPoints + 1));
        // 封装
        SignResultVO vo = new SignResultVO();
//        vo.setSignPoints();
        vo.setSignDays(days);
        vo.setRewardPoints(rewardPoints);

        return vo;
    }

    @Override
    public Byte[] querySignRecords() {
        Long userId = UserContext.getUser();
        LocalDate now = LocalDate.now();
        String monthAndYear = DateTimeFormatter.ofPattern(":yyyyMM").format(now);
        String signRecordKey = RedisConstants.SIGN_RECORD_KEY_PREFIX + userId.toString() + monthAndYear;
        int dayOfMonth = now.getDayOfMonth(); // 这也是list长度
        List<Long> longs = redisTemplate.opsForValue().bitField(signRecordKey,
                BitFieldSubCommands.create()
                        .get(BitFieldSubCommands.BitFieldType.unsigned(dayOfMonth)).valueAt(0));
        if(CollUtils.isEmpty(longs)){
            return new Byte[0];
        }
        // 获取签到记录
        int res = longs.get(0).intValue();
        int offset = dayOfMonth - 1;
        Byte[] temp = new Byte[dayOfMonth];
//        List<Integer> list = new ArrayList<>(dayOfMonth);
        while(offset >= 0){
            temp[offset] = (byte)(res & 1);
            offset--;
            res = res >>> 1;
        }
        return temp;
    }

    private int continuousDays(int dayOfMonth, String signRecordKey) {
        // 从0偏移量开始, 获取本月的数据.
        List<Long> longs = redisTemplate.opsForValue().bitField(signRecordKey,
                BitFieldSubCommands.create()
                        .get(BitFieldSubCommands.BitFieldType.unsigned(dayOfMonth)).valueAt(0));
        if(CollUtils.isEmpty(longs)){
            return 0;
        }
        int res = longs.get(0).intValue();
        log.debug("signRecordKey: {}, num: {}",signRecordKey, res);
        int count = 0;
        while((res & 1) == 1){
            count++;
            res = res >>> 1;
        }
        return count;
    }
}
