package com.tianji.promotion.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.exceptions.BizIllegalException;
import com.tianji.common.utils.BeanUtils;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.StringUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.promotion.domain.dto.CouponFormDTO;
import com.tianji.promotion.domain.dto.CouponIssueFormDTO;
import com.tianji.promotion.domain.po.Coupon;
import com.tianji.promotion.domain.po.CouponScope;
import com.tianji.promotion.domain.po.UserCoupon;
import com.tianji.promotion.domain.query.CouponQuery;
import com.tianji.promotion.domain.vo.CouponDetailVO;
import com.tianji.promotion.domain.vo.CouponPageVO;
import com.tianji.promotion.domain.vo.CouponScopeVO;
import com.tianji.promotion.domain.vo.CouponVO;
import com.tianji.promotion.enums.CouponStatus;
import com.tianji.promotion.enums.ObtainType;
import com.tianji.promotion.enums.UserCouponStatus;
import com.tianji.promotion.mapper.CouponMapper;
import com.tianji.promotion.service.ICouponScopeService;
import com.tianji.promotion.service.ICouponService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.promotion.service.IExchangeCodeService;
import com.tianji.promotion.service.IUserCouponService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.tianji.promotion.enums.CouponStatus.*;

/**
 * <p>
 * 优惠券的规则信息 服务实现类
 * </p>
 *
 * @author zywu
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class CouponServiceImpl extends ServiceImpl<CouponMapper, Coupon> implements ICouponService {

    private final ICouponScopeService scopeService;
    private final IExchangeCodeService exchangeCodeService;
    private final IUserCouponService userCouponService;

    @Override
    @Transactional
    public void saveCoupon(CouponFormDTO dto) {
        Coupon coupon = BeanUtils.copyBean(dto, Coupon.class);
        save(coupon);
        if(!dto.getSpecific()){
            return;
        }
        List<Long> scopes = dto.getScopes();
        if(CollUtils.isEmpty(scopes)){
            // 有范围但是为空, 该抛出异常
            throw new BizIllegalException("分类id不能为空");
        }
        List<CouponScope> csList = scopes.stream().map(new Function<Long, CouponScope>() {
            @Override
            public CouponScope apply(Long aLong) {
                // todo type需要改
                return new CouponScope().setCouponId(coupon.getId()).setBizId(aLong).setType(1);
            }
        }).collect(Collectors.toList());

        scopeService.saveBatch(csList);


    }

    @Override
    public PageDTO<CouponPageVO> queryCouponPage(CouponQuery query) {

        Page<Coupon> page = this.lambdaQuery()
                .eq(query.getType() != null, Coupon::getDiscountType, query.getType())
                .eq(query.getStatus() != null, Coupon::getStatus, query.getStatus())
                .like(StringUtils.isNotBlank(query.getName()), Coupon::getName, query.getName())
                .page(query.toMpPageDefaultSortByCreateTimeDesc());
        List<Coupon> records = page.getRecords();
        if(CollUtils.isEmpty(records)){
            return PageDTO.empty(page);
        }
        List<CouponPageVO> vos = BeanUtils.copyList(records, CouponPageVO.class);

        return PageDTO.of(page, vos);
    }

    @Transactional
    @Override
    public void issueCoupon(Long id, CouponIssueFormDTO dto) {
        log.debug("发放优惠券, 线程名: {}", Thread.currentThread().getName());
        // 1.查询优惠券
        Coupon coupon = getById(dto.getId());
        if (coupon == null) {
            throw new BadRequestException("优惠券不存在！");
        }
        // 2.判断优惠券状态，是否是暂停或待发放
        if(coupon.getStatus() != CouponStatus.DRAFT && coupon.getStatus() != PAUSE){
            throw new BizIllegalException("优惠券状态错误！");
        }
        // 3.判断是否是立刻发放
        LocalDateTime issueBeginTime = dto.getIssueBeginTime();
        LocalDateTime now = LocalDateTime.now();
        boolean isBegin = issueBeginTime == null || !issueBeginTime.isAfter(now);
        // 4.更新优惠券
        // 4.1.拷贝属性到PO
        Coupon c = BeanUtils.copyBean(dto, Coupon.class);
        // 4.2.更新状态
        if (isBegin) {
            c.setStatus(ISSUING);
            c.setIssueBeginTime(now);
        }else{
            c.setStatus(UN_ISSUE);
        }
        // 4.3.写入数据库
        updateById(c);

        // 5.添加缓存
        if (isBegin) {
            coupon.setIssueBeginTime(c.getIssueBeginTime());
            coupon.setIssueEndTime(c.getIssueEndTime());
        }

        // 6.判断是否需要生成兑换码，优惠券类型必须是兑换码，优惠券状态必须是待发放
        if(coupon.getObtainWay() == ObtainType.ISSUE && coupon.getStatus() == CouponStatus.DRAFT){
            coupon.setIssueEndTime(c.getIssueEndTime());
            exchangeCodeService.asyncGenerateExchangeCode(coupon);
        }
    }

    @Override
    @Transactional
    public void deleteCoupon(Long id, CouponFormDTO dto) {
        if(id == null || !id.equals(dto.getId())){
            throw new BadRequestException("参数错误");
        }
        Coupon coupon = getById(id);
        if(coupon == null){
            throw new BizIllegalException("优惠券不存在");
        }
        removeById(id);
        /*List<CouponScope> list = scopeService.lambdaQuery()
                .select(CouponScope::getId)
                .eq(CouponScope::getCouponId, id)
                .list();
        List<Long> csIdList = list.stream().map(CouponScope::getId).collect(Collectors.toList());
        scopeService.removeByIds(csIdList);*/

        // dto如果会传scopes就直接用, 没有就用上面的代码.
        List<Long> scopes = dto.getScopes();
        if(CollUtils.isEmpty(scopes)){
            return;
        }
        scopeService.removeByIds(scopes);
    }

    @Override
    @Transactional
    public void updateCoupon(Long id, CouponFormDTO dto) {
        if(id == null || !id.equals(dto.getId())){
            throw new BadRequestException("参数错误");
        }
        Coupon coupon = getById(id);
        if(coupon == null){
            throw new BizIllegalException("优惠券不存在");
        }
        BeanUtils.copyProperties(dto, coupon);
        updateById(coupon);
        // 更新范围, 可以删除再插入
        List<Long> scopes = dto.getScopes();
        if(CollUtils.isEmpty(scopes)){
            // todo scopes没有就当作没有修改, 还是全部删除?
            return;
        }
        scopeService.remove(new LambdaQueryWrapper<CouponScope>()
                .eq(CouponScope::getCouponId, coupon.getId()));
        List<CouponScope> csList = scopes.stream().map(new Function<Long, CouponScope>() {
            @Override
            public CouponScope apply(Long aLong) {
                // todo type需要改
                return new CouponScope().setCouponId(coupon.getId()).setBizId(aLong).setType(1);
            }
        }).collect(Collectors.toList());

        scopeService.saveBatch(csList);
    }

    @Override
    public CouponDetailVO getCouponDetailVOById(Long id) {
        if(id == null){
            throw new BadRequestException("参数错误");
        }
        Coupon c = getById(id);
        CouponDetailVO couponDetailVO = BeanUtils.copyBean(c, CouponDetailVO.class);
        List<CouponScope> scopeList = scopeService.lambdaQuery().eq(CouponScope::getCouponId, id).list();
        if(!CollUtils.isEmpty(scopeList)){
            List<CouponScopeVO> scopeVOList = new ArrayList<>(scopeList.size());
            for (CouponScope couponScope : scopeList) {
                CouponScopeVO vo = new CouponScopeVO();
                vo.setId(couponScope.getId());
                // todo 根据type去远程调用, 获取name
                Long bizId = couponScope.getBizId();
            }
            couponDetailVO.setScopes(scopeVOList);
        }
        return couponDetailVO;
    }

    @Override
    @Transactional
    public void pauseCoupon(Long id) {
        if(id == null){
            throw new BadRequestException("参数错误");
        }
        Coupon coupon = getById(id);
        if(coupon.getStatus().equalsValue(ISSUING.getValue())){
            coupon.setStatus(PAUSE);
        }
        updateById(coupon);
    }

    // 查询正在发放中的优惠券
    @Override
    public List<CouponVO> queryIssuingCoupons() {
        // 手动领取且正在发放
        List<Coupon> couponList = this.lambdaQuery()
                .eq(Coupon::getObtainWay, ObtainType.PUBLIC)
                .eq(Coupon::getStatus, ISSUING)
                .list();
        if(CollUtils.isEmpty(couponList)){
            return CollUtils.emptyList();
        }

        Set<Long> couponIds = couponList.stream().map(Coupon::getId).collect(Collectors.toSet());

        // 当前用户领到的所有的券
        List<UserCoupon> userCouponList = userCouponService.lambdaQuery()
                .eq(UserCoupon::getUserId, UserContext.getUser())
                .in(UserCoupon::getCouponId, couponIds)
                .list();

        // 优惠券还有剩余 并且 用户已领取数量未超过限领数
        Map<Long, Long> issueMap = userCouponList.stream()
                .collect(Collectors.groupingBy(UserCoupon::getCouponId, Collectors.counting()));

        // 用户是否有已经领取，尚未使用的券: key是优惠券id, value是当前登录用户针对 已领 且 未使用的券数量
        Map<Long, Long> unusedMap = userCouponList.stream()
                .filter(c -> c.getStatus() == UserCouponStatus.UNUSED)
                .collect(Collectors.groupingBy(UserCoupon::getCouponId, Collectors.counting()));

        List<CouponVO> voList = new ArrayList<>();
        for (Coupon c : couponList) {
            CouponVO vo = BeanUtils.copyBean(c, CouponVO.class);
            Long issNum = issueMap.getOrDefault(c.getId(), 0L);
            boolean available = c.getIssueNum() < c.getTotalNum() && issNum.intValue() < c.getUserLimit();
            vo.setAvailable(available);
            boolean received = unusedMap.getOrDefault(c.getId(), 0L) > 0L;
            vo.setReceived(received);
            voList.add(vo);
        }

        return voList;
    }
}
