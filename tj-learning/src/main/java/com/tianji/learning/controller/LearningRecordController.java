package com.tianji.learning.controller;

import com.tianji.api.dto.leanring.LearningLessonDTO;
import com.tianji.learning.domain.dto.LearningRecordFormDTO;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import com.tianji.learning.service.ILearningRecordService;
import com.tianji.learning.domain.po.LearningRecord;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.Api;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;

/**
 * <p>
 * 学习记录表 控制器
 * </p>
 *
 * @author zywu
 */
@Api(tags = "学习记录相关接口")
@RestController
@RequiredArgsConstructor
@RequestMapping("/learning-records")
public class LearningRecordController {

    private final ILearningRecordService recordService;

    /**
     * 查询当前用户指定课程的学习进度
     * @param courseId 课程id
     * @return 课表信息、学习记录及进度信息
     */
    @ApiOperation("查询当前用户指定课程的学习进度")
    @GetMapping("/course/{courseId}")
    public LearningLessonDTO queryLearningRecordByCourse(@PathVariable("courseId") Long courseId){
        return recordService.queryLearningRecordByCourse(courseId);
    }

    @PostMapping
    @ApiOperation("提交学习记录")
    public void addLearningRecord(@RequestBody @Validated LearningRecordFormDTO dto){
        recordService.addLearningRecord(dto);
    }


}
