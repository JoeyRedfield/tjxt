package com.tianji.learning.mapper;

import com.tianji.learning.domain.po.LearningLesson;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * <p>
 * 学生课程表 Mapper 接口
 * </p>
 *
 * @author zywu
 */
public interface LearningLessonMapper extends BaseMapper<LearningLesson> {


    void deleteByMap(@Param("user_id") Long userId, @Param("course_id") List<Long> courseIds);
}
