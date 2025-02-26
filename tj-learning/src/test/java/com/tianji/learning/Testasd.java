package com.tianji.learning;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.tianji.api.client.remark.RemarkClient;
import com.tianji.common.utils.DateUtils;
import com.tianji.learning.domain.po.LearningLesson;
import com.tianji.learning.domain.po.PointsRecord;
import com.tianji.learning.enums.LessonStatus;
import com.tianji.learning.enums.PlanStatus;
import com.tianji.learning.service.ILearningLessonService;
import com.tianji.learning.service.IPointsRecordService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

@SpringBootTest(classes = LearningApplication.class)
public class Testasd {

    @Autowired
    public ILearningLessonService learningLessonService;

    @Test
    public void test1(){
        List<LearningLesson> list = learningLessonService.lambdaQuery().eq(LearningLesson::getUserId, 2)
                .eq(LearningLesson::getPlanStatus, PlanStatus.PLAN_RUNNING)
                .in(LearningLesson::getStatus, LessonStatus.LEARNING, LessonStatus.NOT_BEGIN)
                .list();
        System.out.println(list);
        int plansTotal = list.stream().mapToInt(LearningLesson::getWeekFreq).sum();
        System.out.println(plansTotal);
    }

    @Autowired
    RemarkClient remarkClient;

    @Test
    public void test2(){
        Set<Long> likesStatusByBizIds = remarkClient.getLikesStatusByBizIds(List.of(1893550597718786049L, 456L));
        System.out.println(likesStatusByBizIds);

    }

    @Autowired
    private IPointsRecordService pointsRecordService;

    @Test
    public void test3(){
        QueryWrapper<PointsRecord> wrapper = new QueryWrapper<PointsRecord>();

        LocalDateTime weekBeginTime = DateUtils.getWeekBeginTime(LocalDate.now());
        LocalDateTime weekEndTime = DateUtils.getWeekEndTime(LocalDate.now());

        wrapper.select("sum(points) as totalPoints");
        wrapper.eq("user_id", 2);
        wrapper.eq("type", 2); // type.getValue是对应类型
        wrapper.between("create_time",weekBeginTime, weekEndTime);
        Map<String, Object> map = pointsRecordService.getBaseMapper().selectMaps(wrapper).get(0);
        System.out.println(map);
    }
}
