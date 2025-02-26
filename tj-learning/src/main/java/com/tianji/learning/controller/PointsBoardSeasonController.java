package com.tianji.learning.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.tianji.learning.service.IPointsBoardSeasonService;
import com.tianji.learning.domain.po.PointsBoardSeason;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.Api;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;

/**
 * <p>
 *  控制器
 * </p>
 *
 * @author zywu
 */
@Api(tags = "PointsBoardSeason管理")
@RestController
@RequiredArgsConstructor
@RequestMapping("/pointsBoardSeason")
public class PointsBoardSeasonController {

    private final IPointsBoardSeasonService pointsBoardSeasonService;


}
