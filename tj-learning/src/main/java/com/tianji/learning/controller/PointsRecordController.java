package com.tianji.learning.controller;

import com.tianji.learning.domain.vo.PointsStatisticsVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.tianji.learning.service.IPointsRecordService;
import com.tianji.learning.domain.po.PointsRecord;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.Api;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * <p>
 * 学习积分记录，每个月底清零 控制器
 * </p>
 *
 * @author zywu
 */
@Api(tags = "积分相关接口")
@RestController
@RequiredArgsConstructor
@RequestMapping("/points")
public class PointsRecordController {

    private final IPointsRecordService pointsRecordService;

    @ApiOperation("查询我的今日积分情况")
    @GetMapping("/today")
    public List<PointsStatisticsVO> queryMyTodayPoints(){
        return pointsRecordService.queryMyTodayPoints();
    }

}
