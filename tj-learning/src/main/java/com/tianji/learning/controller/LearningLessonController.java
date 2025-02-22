package com.tianji.learning.controller;

import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.domain.query.PageQuery;
import com.tianji.common.utils.CollUtils;
import com.tianji.learning.domain.dto.LearningPlanDTO;
import com.tianji.learning.domain.vo.LearningLessonVO;
import com.tianji.learning.domain.vo.LearningPlanPageVO;
import io.swagger.annotations.ApiParam;
import org.springframework.web.bind.annotation.*;
import com.tianji.learning.service.ILearningLessonService;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.Api;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;

/**
 * <p>
 * 学生课程表 控制器
 * </p>
 *
 * @author zywu
 */
@Api(tags = "我的课程相关接口(LearningLesson)")
@RestController
@RequiredArgsConstructor
@RequestMapping("/lessons")
public class LearningLessonController {

    private final ILearningLessonService learningLessonService;

    @ApiOperation("查询我的课表")
    @GetMapping("/page")
    public PageDTO<LearningLessonVO> queryMyLessons(PageQuery query){
        return learningLessonService.queryMyLessons(query);
    }

    @ApiOperation("查询正在学习的课程")
    @GetMapping("/now")
    public LearningLessonVO queryMyCurrentLesson(){
        return learningLessonService.queryMyCurrentLesson();
    }

    @ApiOperation("删除课程")
    @DeleteMapping("/{courseId}")
    public void deleteLearningLessons(@PathVariable Long courseId){
        learningLessonService.deleteLearningLessons(CollUtils.singletonList(courseId));
    }

    @ApiOperation("课程是否有效")
    @GetMapping("/{courseId}/valid")
    public Long isLessonValid(@PathVariable Long courseId){
        return learningLessonService.isLessonValid(courseId);
    }

    @ApiOperation("查询当前用户的课表中是否有该课程")
    @GetMapping("/{courseId}")
    public LearningLessonVO learningLessonIsExists(@PathVariable Long courseId){
        return learningLessonService.learningLessonIsExists(courseId);
    }

    @ApiOperation("统计每个课程的报名人数")
    @GetMapping("/{courseId}/count")
    public Integer countLearningLessonByCourse(@PathVariable("courseId") Long courseId){
        return learningLessonService.countLearningLessonByCourse(courseId);
    }

    /**
     * 创建学习计划
     */
    @ApiOperation("创建学习计划")
    @PostMapping("/plans")
    public void createLearningPlan(@RequestBody LearningPlanDTO dto){
        learningLessonService.createLearningPlan(dto);
    }

    @GetMapping("/plans")
    @ApiOperation("分页查询我的课程计划")
    public LearningPlanPageVO queryMyPlans(PageQuery pageQuery){
        return learningLessonService.queryMyPlans(pageQuery);
    }

}
