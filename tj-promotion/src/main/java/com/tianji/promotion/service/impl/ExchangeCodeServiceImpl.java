package com.tianji.promotion.service.impl;

import com.tianji.promotion.constants.PromotionConstants;
import com.tianji.promotion.domain.po.Coupon;
import com.tianji.promotion.domain.po.ExchangeCode;
import com.tianji.promotion.mapper.ExchangeCodeMapper;
import com.tianji.promotion.service.IExchangeCodeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.promotion.utils.CodeUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * <p>
 * 兑换码 服务实现类
 * </p>
 *
 * @author zywu
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ExchangeCodeServiceImpl extends ServiceImpl<ExchangeCodeMapper, ExchangeCode> implements IExchangeCodeService {

    private final StringRedisTemplate redisTemplate;

    @Override
    @Transactional
    @Async("generateExchangeCodeExecutor") // 指定自己声明的线程池. 用自定义的是为了避免内存溢出. 参考ThreadPoolTaskExecutor默认配置
    public void asyncGenerateExchangeCode(Coupon coupon) {
        log.debug("生成兑换码, 线程名: {}", Thread.currentThread().getName());
        Integer totalNum = coupon.getTotalNum();
        if(totalNum == null){
            return;
        }
        Long increment = redisTemplate.opsForValue().increment(PromotionConstants.COUPON_CODE_SERIAL_KEY, totalNum);
        if(increment == null){
            return;
        }
        int maxSerialNum = increment.intValue();
        int begin = maxSerialNum - totalNum + 1;
        List<ExchangeCode> list = new ArrayList<>();
        for(int serialNum = begin; serialNum < maxSerialNum; serialNum++){
            ExchangeCode exchangeCode = new ExchangeCode();
            String code = CodeUtil.generateCode(serialNum, coupon.getId());
            exchangeCode.setId(serialNum);
            exchangeCode.setCode(code);
            exchangeCode.setExpiredTime(coupon.getIssueEndTime());
            exchangeCode.setExchangeTargetId(coupon.getId());
//            exchangeCode.setType(1);
//            exchangeCode.setStatus(ExchangeCodeStatus.UNUSED);

            list.add(exchangeCode);
        }
        this.saveBatch(list);
        redisTemplate.opsForZSet().add(PromotionConstants.COUPON_RANGE_KEY, coupon.getId().toString(), maxSerialNum);
    }

    @Override
    public boolean updateExchangeCodeMark(long serialNum, boolean b) {
        String couponCodeMapKey = PromotionConstants.COUPON_CODE_MAP_KEY;
        Boolean setBit = redisTemplate.opsForValue().setBit(couponCodeMapKey, serialNum, b);
        // 修改成功为true, 可以使用, 修改失败为false, 不可使用
        return setBit != null && setBit;
    }
}
