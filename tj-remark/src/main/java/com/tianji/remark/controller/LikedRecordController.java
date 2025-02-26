package com.tianji.remark.controller;

import com.tianji.remark.domain.dto.LikeRecordFormDTO;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import com.tianji.remark.service.ILikedRecordService;
import com.tianji.remark.domain.po.LikedRecord;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.Api;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * <p>
 * 点赞记录表 控制器
 * </p>
 *
 * @author zywu
 */
@Api(tags = "点赞相关接口管理")
@RestController
@RequiredArgsConstructor
@RequestMapping("/likes")
public class LikedRecordController {

    private final ILikedRecordService likedRecordService;

    @ApiOperation("点赞或取消赞")
    @PostMapping
    public void addLikeRecord(@RequestBody @Validated LikeRecordFormDTO dto){
        likedRecordService.addLikeRecord(dto);
    }

    @ApiOperation("批量查询本用户的点赞状态")
    @GetMapping("/list")
    public Set<Long> getLikesStatusByBizIds(@RequestParam("bizIds") List<Long> bizIds){
        return likedRecordService.getLikesStatusByBizIds(bizIds);
    }

}
