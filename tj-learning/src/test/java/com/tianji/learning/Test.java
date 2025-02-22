package com.tianji.learning;

import com.tianji.learning.domain.po.LearningLesson;
import com.tianji.learning.enums.LessonStatus;
import com.tianji.learning.enums.PlanStatus;
import com.tianji.learning.service.ILearningLessonService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

@SpringBootTest
public class Test {

    @Autowired
    public ILearningLessonService learningLessonService;

    @org.junit.jupiter.api.Test
    public void test1(){
        List<LearningLesson> list = learningLessonService.lambdaQuery().eq(LearningLesson::getUserId, 2)
                .eq(LearningLesson::getPlanStatus, PlanStatus.PLAN_RUNNING)
                .in(LearningLesson::getStatus, LessonStatus.LEARNING, LessonStatus.NOT_BEGIN)
                .list();
        System.out.println(list);
        int plansTotal = list.stream().mapToInt(LearningLesson::getWeekFreq).sum();
        System.out.println(plansTotal);
    }
}
