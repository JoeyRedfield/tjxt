package com.tianji.learning.controller;

import com.tianji.learning.domain.vo.PointsBoardSeasonVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.tianji.learning.service.IPointsBoardSeasonService;
import com.tianji.learning.domain.po.PointsBoardSeason;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.Api;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * <p>
 *  控制器
 * </p>
 *
 * @author zywu
 */
@Api(tags = "赛季相关接口管理")
@RestController
@RequiredArgsConstructor
@RequestMapping("/board/seasons")
public class PointsBoardSeasonController {

    private final IPointsBoardSeasonService pointsBoardSeasonService;

    @GetMapping("/list")
    @ApiOperation("查询赛季列表")
    public List<PointsBoardSeason> list(){
        return pointsBoardSeasonService.list();
    }

}
