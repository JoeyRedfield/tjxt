package com.tianji.promotion.service.impl;

import cn.hutool.core.bean.copier.CopyOptions;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.common.autoconfigure.mq.RabbitMqHelper;
import com.tianji.common.constants.MqConstants;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.exceptions.BizIllegalException;
import com.tianji.common.utils.BeanUtils;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.StringUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.promotion.constants.PromotionConstants;
import com.tianji.promotion.discount.Discount;
import com.tianji.promotion.discount.DiscountStrategy;
import com.tianji.promotion.domain.dto.CouponDiscountDTO;
import com.tianji.promotion.domain.dto.OrderCourseDTO;
import com.tianji.promotion.domain.dto.UserCouponDTO;
import com.tianji.promotion.domain.po.Coupon;
import com.tianji.promotion.domain.po.CouponScope;
import com.tianji.promotion.domain.po.ExchangeCode;
import com.tianji.promotion.domain.po.UserCoupon;
import com.tianji.promotion.enums.CouponStatus;
import com.tianji.promotion.enums.ExchangeCodeStatus;
import com.tianji.promotion.mapper.CouponMapper;
import com.tianji.promotion.mapper.UserCouponMapper;
import com.tianji.promotion.service.ICouponScopeService;
import com.tianji.promotion.service.IExchangeCodeService;
import com.tianji.promotion.service.IUserCouponService;
import com.tianji.promotion.utils.CodeUtil;
import com.tianji.promotion.utils.MyLock;
import com.tianji.promotion.utils.MyLockType;
import com.tianji.promotion.utils.PermuteUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Time;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;

/**
 * <p>
 * 用户领取优惠券的记录，是真正使用的优惠券信息 服务实现类
 * </p>
 *
 * @author zywu
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class UserCouponMqServiceImpl extends ServiceImpl<UserCouponMapper, UserCoupon> implements IUserCouponService {

    private final CouponMapper couponMapper;
    private final IExchangeCodeService exchangeCodeService;
    private final RedissonClient redissonClient;
    private final StringRedisTemplate redisTemplate;
    private final RabbitMqHelper mqHelper;
    private final ICouponScopeService scopeService;
    private final Executor discountSolutionExecutor;

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

    @Override
    public List<CouponDiscountDTO> findDiscountSolution(List<OrderCourseDTO> courses) {
        // 1. 查询当前用户可用的优惠券， 字段见SQL
        List<Coupon> coupons = getBaseMapper().queryMyCoupons(UserContext.getUser());
        if(CollUtils.isEmpty(coupons)){
            return CollUtils.emptyList();
        }
        // 2. 初筛, 总价情况下能用哪些券
        int totalAmount = courses.stream().mapToInt(OrderCourseDTO::getPrice).sum();
        List<Coupon> availableCoupons = coupons.stream()
                .filter(coupon -> DiscountStrategy.getDiscount(coupon.getDiscountType()).canUse(totalAmount, coupon))
                .collect(Collectors.toList());
        if(CollUtils.isEmpty(availableCoupons)){
            return CollUtils.emptyList();
        }
        // 3.排列组合出所有方案
        // 3.1.细筛（找出每一个优惠券的可用的课程，判断课程总价是否达到优惠券的使用需求）
        Map<Coupon, List<OrderCourseDTO>> availableCouponMap = findAvailableCoupon(availableCoupons, courses);
        if (CollUtils.isEmpty(availableCouponMap)) {
            return CollUtils.emptyList();
        }
        // 3.2.排列组合
        availableCoupons = new ArrayList<>(availableCouponMap.keySet());
        List<List<Coupon>> solutions = PermuteUtil.permute(availableCoupons);
        // 3.3.添加单券的方案
        for (Coupon c : availableCoupons) {
            solutions.add(List.of(c));
        }

        // 4. 计算每一种组合的优惠明细
        /*List<CouponDiscountDTO> dtos = new ArrayList<>(solutions.size());
        for (List<Coupon> solution : solutions) {
            CouponDiscountDTO dto = calculateSolutionDiscount(availableCouponMap, courses, solution);
            dtos.add(dto);
        }*/

        // 5. 使用多线程改造4, 并行计算每一种组合的优惠情况
        List<CouponDiscountDTO> dtos = Collections.synchronizedList(new ArrayList<>(solutions.size()));
        CountDownLatch latch = new CountDownLatch(solutions.size());
        for (List<Coupon> solution : solutions) {
            CompletableFuture.supplyAsync(new Supplier<CouponDiscountDTO>() {
                @Override
                public CouponDiscountDTO get() {
                    return calculateSolutionDiscount(availableCouponMap, courses, solution);
                }
            }, discountSolutionExecutor).thenAccept(new Consumer<CouponDiscountDTO>() {
                @Override
                public void accept(CouponDiscountDTO dto) {
                    log.debug("方案最终优惠{}, 方案中优惠券使用了 {} , 规则{}", dto.getDiscountAmount(), dto.getIds(), dto.getRules());
                    dtos.add(dto); // 可以写去上面, 或者换成单纯的多线程, 这里为了把两个业务分开.
                    latch.countDown();
                }
            });
        }
        try {
            latch.await(3, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            log.error("多线程任务报错: ", e);
        }

        // 6. 筛选最优解

        return findBestSolution(dtos);
    }

    /**
     * 求最优解
     * - 用券相同时，优惠金额最高的方案
     * - 优惠金额相同时，用券最少的方案
     * @param solutions
     * @return
     */
    private List<CouponDiscountDTO> findBestSolution(List<CouponDiscountDTO> solutions) {
        // 1.准备Map记录最优解
        Map<String, CouponDiscountDTO> moreDiscountMap = new HashMap<>();
        Map<Integer, CouponDiscountDTO> lessCouponMap = new HashMap<>();

        for (CouponDiscountDTO solution : solutions) {
            // 对优惠券id升序转字符串, 以逗号拼接
            String ids = solution.getIds().stream()
                    .sorted(Comparator.comparingLong(Long::longValue))
                    .map(String::valueOf)
                    .collect(Collectors.joining(","));
            CouponDiscountDTO old = moreDiscountMap.get(ids);
            // 不为空, 且旧方案的折扣价格 大于 现在方案的折扣价格
            if(old != null && old.getDiscountAmount() >= solution.getDiscountAmount()){
                continue;
            }
            // 新方案折扣价格更高, 就找优惠金额相同时, 用券最少的方案
            old = lessCouponMap.get(solution.getDiscountAmount());
            if(old != null && old.getIds().size() > 1 && old.getIds().size() <= solution.getIds().size()){
                continue;
            }
            // 此时是多券情况下, 新方案比旧方案的用券更少
            moreDiscountMap.put(ids, solution);
            lessCouponMap.put(solution.getDiscountAmount(), solution);
        }
        Collection<CouponDiscountDTO> bestSolution = CollUtils.intersection(moreDiscountMap.values(), lessCouponMap.values());
        // 求交集, 然后对最终方案结果, 按优惠金额 倒序
        List<CouponDiscountDTO> latestBestSolution = bestSolution.stream()
                .sorted(Comparator.comparing(CouponDiscountDTO::getDiscountAmount).reversed())
                .collect(Collectors.toList());
        return latestBestSolution;
    }

    /**
     * 计算每一个方案的 优惠信息
     * @param availableCouponMap 优惠券和可用课程的映射集合
     * @param courses 订单中所有的课程
     * @param solution 方案
     * @return
     */
    private CouponDiscountDTO calculateSolutionDiscount(Map<Coupon, List<OrderCourseDTO>> availableCouponMap, List<OrderCourseDTO> courses, List<Coupon> solution) {
        // 1.创建方案结果dto对象
        CouponDiscountDTO dto = new CouponDiscountDTO();
        // 2.初始化商品id和商品折扣明细的映射, 初始折扣明细全都设置成0
        Map<Long, Integer> detailMap = courses.stream()
                .collect(Collectors.toMap(OrderCourseDTO::getId, oc -> 0));
        // 3.循环方案中优惠券, 计算该方案的优惠信息
        for (Coupon coupon : solution) {
            // 3.1.获取优惠券限定范围对应的课程
            List<OrderCourseDTO> availableCourses = availableCouponMap.get(coupon);
            // 3.2计算可用课程的总金额(商品价格 - 该商品的折扣明细)
            int totalAmount = availableCourses.stream().mapToInt(value -> value.getPrice() - detailMap.get(value.getId())).sum();
            // 3.3判断优惠券是否可用
            Discount discount = DiscountStrategy.getDiscount(coupon.getDiscountType());
            if(!discount.canUse(totalAmount, coupon)){
                continue;
            }
            int discountAmount = discount.calculateDiscount(totalAmount, coupon);
            // 3.4计算该优惠券使用后的折扣值(优惠金额)
            calculateDiscountDetails(detailMap, availableCourses, totalAmount, discountAmount);
            // 3.6更新商品的折扣明细(商品id和该商品折扣明细)
            dto.getIds().add(coupon.getCreater());
            dto.getRules().add(discount.getRule(coupon));
            // 3.7累加每一个优惠券的优惠金额, 赋值给方案结果dto对象
            dto.setDiscountAmount(discountAmount + dto.getDiscountAmount());
        }
        return dto;
    }

    private void calculateDiscountDetails(Map<Long, Integer> detailMap,
                                          List<OrderCourseDTO> courses,
                                          int totalAmount, int discountAmount) {
        int times = 0;
        int remainDiscount = discountAmount;
        for (OrderCourseDTO c : courses) {
            times++;
            int discount = 0;
            if (times == courses.size()){
                discount = remainDiscount;
            } else {
                discount = discountAmount * c.getPrice() / totalAmount;
                remainDiscount -= discount;
            }
            detailMap.put(c.getId(), discount + detailMap.get(c.getId()));
        }
    }

    /**
     * 细筛的排列组合
     * @param coupons
     * @param courses
     * @return
     */
    private Map<Coupon, List<OrderCourseDTO>> findAvailableCoupon(List<Coupon> coupons, List<OrderCourseDTO> courses) {
        Map<Coupon, List<OrderCourseDTO>> map = new HashMap<>();
        for (Coupon coupon : coupons) {
            List<OrderCourseDTO> availableCourses = courses;
            if(coupon.getSpecific()) {
                // 如果限定了特定范围, 找出每一个优惠券的可用课程
                List<CouponScope> scopeList = scopeService.lambdaQuery()
                        .eq(CouponScope::getCouponId, coupon.getId())
                        .select(CouponScope::getBizId).list();// 优惠券能适用的课程
                List<Long> scopeIds = scopeList.stream().map(CouponScope::getBizId).collect(Collectors.toList());
                // 用了外部的变量是为了处理null的情况.
                availableCourses = courses.stream()
                        .filter(orderCourseDTO -> scopeIds.contains(orderCourseDTO.getCateId()))
                        .collect(Collectors.toList());
            }
            if(CollUtils.isEmpty(availableCourses)){
                continue; // 因为没在courses中找到优惠券能用的课程, 所以dtos会为空.
            }
            int totalAmount = availableCourses.stream().mapToInt(OrderCourseDTO::getPrice).sum(); // 可用课程的总金额

            Discount discount = DiscountStrategy.getDiscount(coupon.getDiscountType());
            if(discount.canUse(totalAmount, coupon)){
                map.put(coupon, availableCourses); // 优惠券, 以及它对应的能用的课程
            }
        }
        return map;
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
