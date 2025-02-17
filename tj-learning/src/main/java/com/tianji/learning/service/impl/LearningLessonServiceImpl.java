package com.tianji.learning.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.conditions.query.QueryChainWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tianji.api.client.course.CatalogueClient;
import com.tianji.api.client.course.CourseClient;
import com.tianji.api.client.learning.LearningClient;
import com.tianji.api.dto.course.CataSimpleInfoDTO;
import com.tianji.api.dto.course.CourseFullInfoDTO;
import com.tianji.api.dto.course.CourseSearchDTO;
import com.tianji.api.dto.course.CourseSimpleInfoDTO;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.domain.query.PageQuery;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.exceptions.BizIllegalException;
import com.tianji.common.utils.BeanUtils;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.domain.po.LearningLesson;
import com.tianji.learning.domain.vo.LearningLessonVO;
import com.tianji.learning.enums.LessonStatus;
import com.tianji.learning.mapper.LearningLessonMapper;
import com.tianji.learning.service.ILearningLessonService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * <p>
 * 学生课程表 服务实现类
 * </p>
 *
 * @author zywu
 */
@Service
@RequiredArgsConstructor
public class LearningLessonServiceImpl extends ServiceImpl<LearningLessonMapper, LearningLesson> implements ILearningLessonService {

    final CourseClient courseClient;
    final CatalogueClient catalogueClient;
    final LearningClient learningClient;
    final LearningLessonMapper learningLessonMapper;

    @Override
    @Transactional
    public void addUserLesson(Long userId, List<Long> courseIds) {
        List<CourseSimpleInfoDTO> simpleInfoList = courseClient.getSimpleInfoList(courseIds);
        if (CollUtils.isEmpty(simpleInfoList)) {
            log.error("课程信息不存在, 无法添加到列表");
            return;
        }
        List<LearningLesson> list = new ArrayList<>(simpleInfoList.size());
        for (CourseSimpleInfoDTO cinfo : simpleInfoList) {
            LearningLesson l = new LearningLesson();
            Integer validDuration = cinfo.getValidDuration();
            if (validDuration != null && validDuration > 0) {
                LocalDateTime now = LocalDateTime.now();
                l.setCreateTime(now);
                l.setExpireTime(now.plusMonths(validDuration));
            }
            l.setCourseId(cinfo.getId());
            l.setUserId(userId);
            list.add(l);
        }
        saveBatch(list);

    }

    @Override
    public PageDTO<LearningLessonVO> queryMyLessons(PageQuery query) {
        // 获取当前用户
        Long userId = UserContext.getUser();
        // 有拦截器，不用写
//        if(userId == null){
//            throw new BadRequestException("用户未登录");
//        }

        // 分页查询我的课表
        Page<LearningLesson> page = lambdaQuery()
                .eq(LearningLesson::getUserId, userId)
                .page(query.toMpPage("latest_learn_time", false));
        List<LearningLesson> records = page.getRecords();
        if (CollUtils.isEmpty(records)) {
            return PageDTO.empty(page);
        }

        // 远程调用课程服务，给vo中的课程名、封面、章节数赋值
        List<Long> cids = page.getRecords().stream().map(LearningLesson::getCourseId).collect(Collectors.toList());
        List<CourseSimpleInfoDTO> cinfos = courseClient.getSimpleInfoList(cids);
        if (CollUtils.isEmpty(cinfos)) {
            // 业务异常
            throw new BizIllegalException("课程不存在");
        }

        Map<Long, CourseSimpleInfoDTO> infoDTOMap = cinfos.stream().collect(Collectors.toMap(CourseSimpleInfoDTO::getId, c -> c));
        List<LearningLessonVO> res = new ArrayList<>(records.size());
        // 将po中的数据，封装到vo中
        for (LearningLesson record : records) {
            LearningLessonVO vo = BeanUtils.copyBean(record, LearningLessonVO.class);
            CourseSimpleInfoDTO dto = infoDTOMap.get(record.getCourseId());
            if (dto != null) {
                vo.setCourseCoverUrl(dto.getCoverUrl());
                vo.setCourseName(dto.getName());
                vo.setSections(dto.getSectionNum());
                res.add(vo);
            }
        }
        return PageDTO.of(page, res);
    }

    @Override
    public LearningLessonVO queryMyCurrentLesson() {
        Long userId = UserContext.getUser();

        // 根据当前用户id，找到正在学习的课程，按照最新学习时间降序排序，取第一个，就是正在学习的课程。
        LearningLesson lesson = lambdaQuery().eq(LearningLesson::getUserId, userId)
                .eq(LearningLesson::getStatus, LessonStatus.LEARNING)
                .orderByDesc(LearningLesson::getLatestLearnTime)
                .last("limit 1")
                .one();
        if (lesson == null) {
            return null;
        }

        LearningLessonVO vo = BeanUtils.copyBean(lesson, LearningLessonVO.class);

        // 远程调用课程服务，获取课程名、封面、章节数
        CourseFullInfoDTO cinfo = courseClient.getCourseInfoById(vo.getCourseId(), false, false);
        if (cinfo == null) {
            throw new BizIllegalException("课程不存在");
        }
        vo.setCourseCoverUrl(cinfo.getCoverUrl());
        vo.setCourseName(cinfo.getName());
        vo.setSections(cinfo.getSectionNum());

        // 课表中课程总数
        Integer count = lambdaQuery().eq(LearningLesson::getUserId, userId).count();
        vo.setCourseAmount(count);

        Long latestSectionId = lesson.getLatestSectionId();

        // 远程调用，获取小结名称、小结编号
        CataSimpleInfoDTO cataSimpleInfoDTO = catalogueClient.batchQueryCatalogue(CollUtils.singletonList(latestSectionId)).get(0);
        if (cataSimpleInfoDTO == null) {
            throw new BizIllegalException("小节不存在");
        }
        vo.setLatestSectionName(cataSimpleInfoDTO.getName());
        vo.setLatestSectionIndex(cataSimpleInfoDTO.getCIndex());

        return vo;
    }

    @Override
    public void deleteLearningLessons(List<Long> courseIds) {
        Long userId = UserContext.getUser();
        if (CollUtils.isEmpty(courseIds)) {
            return;
        }
        learningLessonMapper.deleteByMap(userId, courseIds);

    }

//    @Override
//    public Long isLessonValid(Long courseId) {
//        Long userId = UserContext.getUser();
//        if (courseId == null) {
//            // 用户过了拦截链就是存在的，而课程不一定
//            return null;
//        }
//        // 不需要返回对象，只需要知道有就行.
//        LearningLesson lesson = lambdaQuery()
//                .eq(LearningLesson::getUserId, userId)
//                .eq(LearningLesson::getCourseId, courseId) // - 用户课表中是否有该课程
//                .ge(LearningLesson::getExpireTime, LocalDateTime.now()) // - 课程状态是否是有效的状态（未过期） 过期时间>=现在，就有效
//                .one();
//
//        if (lesson == null) {
//            // 没找到符合条件的，就不合法
//            return null;
//        }
//        // 前面的判断都过了，说明用户：当前用户的课表中有该课程，并且课程状态有效
//        return lesson.getId();
//    }

    @Override
    public Long isLessonValid(Long courseId) {
        Long userId = UserContext.getUser();
        if (courseId == null) {
            // 用户过了拦截链就是存在的，而课程不一定
            return null;
        }
        // 不需要返回对象，只需要知道有就行.
        LearningLesson lesson = lambdaQuery()
                .eq(LearningLesson::getUserId, userId)
                .eq(LearningLesson::getCourseId, courseId) // - 用户课表中是否有该课程
                .one();

        if (lesson == null) {
            // 没找到符合条件的，就不合法
            return null;
        }

        LocalDateTime expireTime = lesson.getExpireTime();
        if(expireTime != null && LocalDateTime.now().isAfter(expireTime)){
            return null;
        }

        // 前面的判断都过了，说明用户：当前用户的课表中有该课程，并且课程状态有效
        return lesson.getId();
    }

    @Override
    public LearningLessonVO learningLessonIsExists(Long courseId) {
        if (courseId == null) {
            throw new BizIllegalException("课程不存在");
        }
        Long userId = UserContext.getUser();
        // 先根据用户id和课程id查询当前用户课表是否有该课程
        // {用户id, 课程id}是唯一的.
        LearningLesson lesson = lambdaQuery().eq(LearningLesson::getUserId, userId)
                .eq(LearningLesson::getCourseId, courseId)
//                .last("limit 1")
                .one();
        if (lesson == null) {
            return null;
        }
        // 根据返回值格式, 不需要再往vo加新内容.
        return BeanUtils.copyBean(lesson, LearningLessonVO.class);
    }

    @Override
    public Integer countLearningLessonByCourse(Long courseId) {
        if (courseId == null) {
            return 0;
        }
        return lambdaQuery().eq(LearningLesson::getCourseId, courseId).count();
    }
}
