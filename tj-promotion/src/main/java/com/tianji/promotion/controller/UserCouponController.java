package com.tianji.promotion.controller;

import com.tianji.promotion.domain.dto.CouponDiscountDTO;
import com.tianji.promotion.domain.dto.OrderCourseDTO;
import org.springframework.web.bind.annotation.*;
import com.tianji.promotion.service.IUserCouponService;
import com.tianji.promotion.domain.po.UserCoupon;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.Api;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * <p>
 * 用户领取优惠券的记录，是真正使用的优惠券信息 控制器
 * </p>
 *
 * @author zywu
 */
@Api(tags = "用户券 管理")
@RestController
@RequiredArgsConstructor
@RequestMapping("/user-coupons")
public class UserCouponController {

    private final IUserCouponService userCouponService;

    @ApiOperation("领取优惠券")
    @PostMapping("/{id}/receive")
    public void receiveCoupon(@PathVariable Long id){
        userCouponService.receiveCoupon(id);
    }

    @ApiOperation("兑换码兑换优惠券")
    @PostMapping("/{code}/exchange")
    public void exchangeCoupon(@PathVariable String code){
        userCouponService.exchangeCoupon(code);
    }

    /**
     * 该方法是给tj-trade服务 远程调用使用的
     * @param courses 订单中的课程信息
     * @return 方案集合
     */
    @ApiOperation("查询可用优惠券方案")
    @PostMapping("/available")
    public List<CouponDiscountDTO> findDiscountSolution(@RequestBody List<OrderCourseDTO> courses){
        return userCouponService.findDiscountSolution(courses);
    }


}
