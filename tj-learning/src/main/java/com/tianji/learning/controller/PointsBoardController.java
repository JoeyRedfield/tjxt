package com.tianji.learning.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.tianji.learning.service.IPointsBoardService;
import com.tianji.learning.domain.po.PointsBoard;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.Api;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;

/**
 * <p>
 * 学霸天梯榜 控制器
 * </p>
 *
 * @author zywu
 */
@Api(tags = "PointsBoard管理")
@RestController
@RequiredArgsConstructor
@RequestMapping("/pointsBoard")
public class PointsBoardController {

    private final IPointsBoardService pointsBoardService;


}
