package com.tianji.promotion.service.impl;

import cn.hutool.core.bean.copier.CopyOptions;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.common.autoconfigure.mq.RabbitMqHelper;
import com.tianji.common.constants.MqConstants;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.exceptions.BizIllegalException;
import com.tianji.common.utils.BeanUtils;
import com.tianji.common.utils.StringUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.promotion.constants.PromotionConstants;
import com.tianji.promotion.domain.dto.UserCouponDTO;
import com.tianji.promotion.domain.po.Coupon;
import com.tianji.promotion.domain.po.ExchangeCode;
import com.tianji.promotion.domain.po.UserCoupon;
import com.tianji.promotion.enums.CouponStatus;
import com.tianji.promotion.enums.ExchangeCodeStatus;
import com.tianji.promotion.mapper.CouponMapper;
import com.tianji.promotion.mapper.UserCouponMapper;
import com.tianji.promotion.service.IExchangeCodeService;
import com.tianji.promotion.service.IUserCouponService;
import com.tianji.promotion.utils.CodeUtil;
import com.tianji.promotion.utils.MyLock;
import com.tianji.promotion.utils.MyLockType;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * <p>
 * 用户领取优惠券的记录，是真正使用的优惠券信息 服务实现类
 * </p>
 *
 * @author zywu
 */
@Service
@RequiredArgsConstructor
public class UserCouponMqServiceImpl extends ServiceImpl<UserCouponMapper, UserCoupon> implements IUserCouponService {

    private final CouponMapper couponMapper;
    private final IExchangeCodeService exchangeCodeService;
    private final RedissonClient redissonClient;
    private final StringRedisTemplate redisTemplate;
    private final RabbitMqHelper mqHelper;
//    private final ICouponService couponService;

    @Override
    // 分布式锁可以 对 优惠券加锁
    @MyLock(name = "lock:coupon:#{id}")
    public void receiveCoupon(Long id) {
        if(id == null){
            throw new BadRequestException("非法参数");
        }
//        Coupon coupon = couponMapper.selectById(id);
        // 从redis获取优惠券信息
        Coupon coupon = queryCouponByCache(id);
        if(coupon == null) {
            throw new BadRequestException("优惠券不存在");
        }
        // 从redis取的, 不会有status, 但必然是正在发放, 所以不用判断
        /*if(coupon.getStatus() != CouponStatus.ISSUING){
            throw new BadRequestException("优惠券不在发放状态");
        }*/
//        if(coupon.getTotalNum() <= 0 || coupon.getTotalNum() <= coupon.getIssueNum()){
        if(coupon.getTotalNum() <= 0){
            throw new BadRequestException("优惠券库存不足");
        }
        LocalDateTime issueBeginTime = coupon.getIssueBeginTime();
        LocalDateTime issueEndTime = coupon.getIssueEndTime();
        LocalDateTime now = LocalDateTime.now();
        // 如果now比发放时间早, 或者比结束时间晚
        if(now.isBefore(issueBeginTime) || now.isAfter(issueEndTime)){
            throw new BadRequestException("不在发放时间内");
        }

        Long userId = UserContext.getUser();
//        Integer count = this.lambdaQuery()
//                .eq(UserCoupon::getUserId, userId)
//                .eq(UserCoupon::getCouponId, id)
//                .count();
//        if(count != null && coupon.getUserLimit() <= count){
//            throw new BadRequestException("优惠券达到领取上限");
//        }
//        couponMapper.incrIssueNum(id); // TODO: 考虑并发控制
//
//        saveUserCoupon(userId, coupon);

//        synchronized (userId.toString().intern()){
//            // 先获取锁, 再开启事务
//            checkAndCreateUserCoupon(userId, coupon, null);
//        }


//        synchronized (userId.toString().intern()){
//            // 先获取锁, 再开启事务
//            // 从aop上下文中, 获取当前类的代理对象
//            IUserCouponService userCouponService = (IUserCouponService)AopContext.currentProxy();
//            // 调用代理对象的方法, 方法是有事务处理的.
//            userCouponService.checkAndCreateUserCoupon(userId, coupon, null);
//        }

//        String key = "lock:coupon:uid" + userId;
//        RLock lock = redissonClient.getLock(key);
//
//        try {
////            lock.tryLock(1,20,TimeUnit.SECONDS);
//            boolean isLock = lock.tryLock(); // 默认情况下, 看门狗机制才会生效, 默认失效时间30s
//            if(!isLock){
//                throw new BizIllegalException("操作频繁");
//            }
//            IUserCouponService userCouponService = (IUserCouponService)AopContext.currentProxy();
//            // 调用代理对象的方法, 方法是有事务处理的.
//            userCouponService.checkAndCreateUserCoupon(userId, coupon, null);
//
//        } finally {
//            lock.unlock();
//        }

//        String key = "lock:coupon:uid" + userId;
//        RLock lock = redissonClient.getLock(key);

        /*IUserCouponService userCouponService = (IUserCouponService)AopContext.currentProxy();
        // 调用代理对象的方法, 方法是有事务处理的.
        userCouponService.checkAndCreateUserCoupon(userId, coupon, null);*/
        // 统计已领取的数量
        String key = PromotionConstants.USER_COUPON_CACHE_KEY_PREFIX + id;
        // prs:user:coupon:优惠券id
        Long increment = redisTemplate.opsForHash().increment(key, userId.toString(), 1);
        // 校验是否超过限领数量, 这里redis返回的的increment因为是先增加所以会很大
        if(increment > coupon.getUserLimit()){ //increment是+1之后的结果, 所以这里是大于.
            throw new BizIllegalException("超出限领数量");
        }
        // 修改优惠券的库存 -1
        String couponKey = PromotionConstants.COUPON_CACHE_KEY_PREFIX + id;
        redisTemplate.opsForHash().increment(couponKey, "totalNum", -1);

        UserCouponDTO msg = new UserCouponDTO();
        msg.setUserId(userId);
//        msg.setCouponId(coupon.getId());
        msg.setCouponId(id);
        mqHelper.send(
                MqConstants.Exchange.PROMOTION_EXCHANGE,
                MqConstants.Key.COUPON_RECEIVE,
                msg);

    }

    /**
     * 从redis获取优惠券信息(领取开始和结束时间, 发行总数量, 限领数量)
     * @param id
     * @return
     */
    private Coupon queryCouponByCache(Long id) {
        String key = PromotionConstants.COUPON_CACHE_KEY_PREFIX + id;
        Map entries = redisTemplate.opsForHash().entries(key);
        Coupon coupon = BeanUtils.mapToBean(entries, Coupon.class, false, CopyOptions.create());
        return coupon;
    }

    @Transactional
    @Override
    public void checkAndCreateUserCouponNew(UserCouponDTO msg) {
        // 只要发消息过来就说明可以领, 因为已经做过校验了
        Coupon coupon = couponMapper.selectById(msg.getCouponId());
        if(coupon==null){
            // throw会导致mq重试
            return;
        }
        int num = couponMapper.incrIssueNum(coupon.getId());
        if(num == 0){
            return;
        }
        saveUserCoupon(msg.getUserId(), coupon);
    }


    @MyLock(name = "lock:coupon:#{userId}", lockType = MyLockType.RE_ENTRANT_LOCK)
    @Transactional
    public void checkAndCreateUserCoupon(Long userId, Coupon coupon, Long serialNum) {
//        synchronized (userId.toString().intern()){
        Integer count = this.lambdaQuery()
                .eq(UserCoupon::getUserId, userId)
                .eq(UserCoupon::getCouponId, coupon.getId())
                .count();
        if(count != null && coupon.getUserLimit() <= count){
            throw new BadRequestException("优惠券达到领取上限");
        }
        couponMapper.incrIssueNum(coupon.getId());

        saveUserCoupon(userId, coupon);
        if(serialNum != null){
            exchangeCodeService.lambdaUpdate()
                    .eq(ExchangeCode::getExchangeTargetId, coupon.getId())
                    .eq(ExchangeCode::getId, serialNum)
                    .set(ExchangeCode::getUserId, userId)
                    .set(ExchangeCode::getType, ExchangeCodeStatus.USED)
                    .update();
        }
    }


    @Override
    @Transactional
    public void exchangeCoupon(String code) {
        if(StringUtils.isBlank(code)){
            throw new BadRequestException("兑换码为空");
        }
        long serialNum = CodeUtil.parseCode(code); // 兑换码的自增id
        boolean result = exchangeCodeService.updateExchangeCodeMark(serialNum, true);
        if(result){
            throw new BizIllegalException("兑换码已经被使用");
        }
        // 因为@Transactional只能回滚数据库操作, 要保证redis的回滚需要try-catch处理
        try{
            ExchangeCode exchangeCode = exchangeCodeService.getById(serialNum);
            if(exchangeCode == null){
                throw new BadRequestException("兑换码不存在");
            }
            if(LocalDateTime.now().isAfter(exchangeCode.getExpiredTime())){
                throw new BadRequestException("兑换码已经过期");
            }
            Coupon coupon = couponMapper.selectById(exchangeCode.getExchangeTargetId());
            if(coupon == null){
                throw new BadRequestException("目标优惠券不存在");
            }
            if(coupon.getTotalNum() <= coupon.getIssueNum()){
                throw new BadRequestException("兑换码库存不足");
            }
            Long userId = UserContext.getUser();

            checkAndCreateUserCoupon(userId, coupon, serialNum);
        }catch (Exception e){
            // 报错就得把优惠券状态重置.
            exchangeCodeService.updateExchangeCodeMark(serialNum, false);
            throw e;
        }
    }

    private void saveUserCoupon(Long userId, Coupon coupon) {
        UserCoupon userCoupon = new UserCoupon();
        userCoupon.setCouponId(coupon.getId());
        userCoupon.setUserId(userId);
        LocalDateTime termBeginTime = coupon.getTermBeginTime();
        LocalDateTime termEndTime = coupon.getTermEndTime();
        if(termBeginTime == null && termEndTime == null){
            // 说明是固定天数, 而非时间段
            LocalDateTime now = LocalDateTime.now();
            termBeginTime = now;
            termEndTime = now.plusDays(coupon.getTermDays());
        }
        userCoupon.setTermBeginTime(termBeginTime);
        userCoupon.setTermEndTime(termEndTime);
        save(userCoupon);
    }


}
