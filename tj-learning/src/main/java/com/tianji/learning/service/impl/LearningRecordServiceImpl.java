package com.tianji.learning.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.tianji.api.client.course.CourseClient;
import com.tianji.api.dto.course.CourseFullInfoDTO;
import com.tianji.api.dto.leanring.LearningLessonDTO;
import com.tianji.api.dto.leanring.LearningRecordDTO;
import com.tianji.common.exceptions.BizIllegalException;
import com.tianji.common.exceptions.DbException;
import com.tianji.common.utils.BeanUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.domain.dto.LearningRecordFormDTO;
import com.tianji.learning.domain.po.LearningLesson;
import com.tianji.learning.domain.po.LearningRecord;
import com.tianji.learning.enums.LessonStatus;
import com.tianji.learning.enums.SectionType;
import com.tianji.learning.mapper.LearningLessonMapper;
import com.tianji.learning.mapper.LearningRecordMapper;
import com.tianji.learning.service.ILearningRecordService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.learning.utils.LearningRecordDelayTaskHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.validation.constraints.NotNull;
import java.util.List;

/**
 * <p>
 * 学习记录表 服务实现类
 * </p>
 *
 * @author zywu
 */
@Service
@RequiredArgsConstructor
public class LearningRecordServiceImpl extends ServiceImpl<LearningRecordMapper, LearningRecord> implements ILearningRecordService {

    private final LearningLessonMapper learningLessonMapper;
    private final CourseClient courseClient;
    private final LearningRecordDelayTaskHandler taskHandler;

    @Override
    public LearningLessonDTO queryLearningRecordByCourse(Long courseId) {
        // 1.获取用户信息
        Long userId = UserContext.getUser();

        // 2. 根据用户id和课程id，查询课表信息. 这是唯一的.
        LearningLesson learningLesson = learningLessonMapper.selectOne(new LambdaQueryWrapper<LearningLesson>()
                .eq(LearningLesson::getUserId, userId)
                .eq(LearningLesson::getCourseId, courseId));
        if(learningLesson == null){
            throw new BizIllegalException("该课程未加入课表.");
        }
        // 3. 拿到课表id，去查记录,
        // 一个课表会有多个课程, 这才叫学习记录.
        List<LearningRecord> list = lambdaQuery()
                .eq(LearningRecord::getLessonId, learningLesson.getId())
                .eq(LearningRecord::getUserId, userId)// userId有没有都行
                .list();

        // 4.封装对象
        LearningLessonDTO dto = new LearningLessonDTO();
        dto.setLatestSectionId(learningLesson.getLatestSectionId());
        dto.setId(learningLesson.getId());
        dto.setRecords(BeanUtils.copyList(list, LearningRecordDTO.class));

        return dto;
    }

    @Override
    @Transactional
    public void addLearningRecord(LearningRecordFormDTO dto) {
        Long userId = UserContext.getUser();
        boolean isFinished = false;
        if(dto.getSectionType().equals(SectionType.EXAM)){
            isFinished = handleExamRecord(userId, dto);
        } else {
            isFinished = handleVideoRecord(userId, dto);
        }
        if(!isFinished){
            return; // 加入了redis后, 如果还没学完, 就不用处理/更新课表.
        }
//        handleLessonData(dto, isFinished); // 这里必然是true了.
        handleLessonData(dto);
    }

    private void handleLessonData(LearningRecordFormDTO dto) {
        Long lessonId = dto.getLessonId();
        LearningLesson lesson = learningLessonMapper.selectById(lessonId);
        if(lesson == null){
            throw new BizIllegalException("课表不存在");
        }
        boolean allFinished = false;
        // 判断是否是第一次学完
//        if(isFinished){
        CourseFullInfoDTO cinfo = courseClient.getCourseInfoById(lesson.getCourseId(), false, false);
        if(cinfo == null){
            throw new BizIllegalException("课程不存在");
        }
        Integer sectionNum = cinfo.getSectionNum();

        Integer learnedSections = lesson.getLearnedSections();
        allFinished = learnedSections + 1 >= sectionNum;
//        }

        learningLessonMapper.update(lesson,
                new LambdaUpdateWrapper<LearningLesson>()
                        .set(allFinished, LearningLesson::getStatus, LessonStatus.FINISHED) // 学完则改为完成
                        .set(lesson.getStatus()==LessonStatus.NOT_BEGIN, LearningLesson::getStatus, LessonStatus.LEARNING) // 没学完则改为正在学习
                        .set(LearningLesson::getLatestSectionId, dto.getSectionId())
                        .set(LearningLesson::getLatestLearnTime, dto.getCommitTime())
                        .setSql("learned_sections = learned_sections + 1") // 相比于常规写法,适合并发
                        .eq(LearningLesson::getId, lesson.getId()));
    }

    private boolean handleVideoRecord(Long userId, LearningRecordFormDTO dto) {

        LearningRecord record = queryOldRecord(dto.getLessonId(), dto.getSectionId());
//        LearningRecord record = lambdaQuery()
//                .eq(LearningRecord::getLessonId, dto.getLessonId()) // lessonId可以由userId+courseId决定
//                .eq(LearningRecord::getSectionId, dto.getSectionId())
//                .one();
        boolean isFinished = false;
        if(record == null){
            record = BeanUtils.copyBean(dto, LearningRecord.class);
            record.setUserId(userId);
            boolean save = save(record);
            if(!save){
                throw new DbException("视频记录添加失败.");
            }
        } else {
            // !注意是取反.
            isFinished = !record.getFinished() && record.getMoment() * 2 >= dto.getDuration();
            if(!isFinished){
                LearningRecord redisRecord = new LearningRecord();

                redisRecord.setLessonId(dto.getLessonId());
                redisRecord.setSectionId(dto.getSectionId());

                redisRecord.setMoment(dto.getMoment());
                redisRecord.setFinished(record.getFinished());
                redisRecord.setId(record.getId());

                taskHandler.addLearningRecordTask(redisRecord);

                return false; //缓存到Redis之后, 就没必要走更新学习记录的路线了.
            }
            // 如果不进上面的if, 那么isFinished=true
            boolean update = lambdaUpdate()
                    .set(isFinished, LearningRecord::getFinished, true)
                    .set(isFinished, LearningRecord::getFinishTime, dto.getCommitTime())
                    .set(LearningRecord::getMoment, dto.getMoment())
                    .set(LearningRecord::getUpdateTime, dto.getCommitTime())
                    .eq(LearningRecord::getLessonId, dto.getLessonId())
                    .eq(LearningRecord::getSectionId, dto.getSectionId()) //recordId也够了, 因为已经查出来了.
                    .update();
            if(!update){
                throw new DbException("视频记录更新失败.");
            }

            // 更新过学习记录, 就去清除redis缓存
            taskHandler.cleanRecordCache(dto.getLessonId(), dto.getSectionId());
        }
        return isFinished;
    }

    private LearningRecord queryOldRecord(@NotNull(message = "课表id不能为空") Long lessonId, @NotNull(message = "节的id不能为空") Long sectionId) {
        // 1. 查询缓存
        LearningRecord cache = taskHandler.readRecordCache(lessonId, sectionId);
        // 2. 如果命中直接返回
        if(cache != null){
            return cache;
        }
        // 3. 如果未命中 查询db
        LearningRecord dbRecord = lambdaQuery()
                .eq(LearningRecord::getLessonId, lessonId)
                .eq(LearningRecord::getSectionId, sectionId)
                .one();
        // 4. 放入缓存
        if(dbRecord == null){
            return null; //db和redis都找不到, 就要插入新数据
        }
        // 必须要有null判断, 不然第一次播放视频时, db和redis都找不到数据, write方法会报空指针异常.
        taskHandler.writeRecordCache(dbRecord);
        return dbRecord;
    }

    private boolean handleExamRecord(Long userId, LearningRecordFormDTO dto) {
        LearningRecord record = BeanUtils.copyBean(dto, LearningRecord.class);
        record.setUserId(userId);
        record.setFinished(true);
        record.setFinishTime(dto.getCommitTime());
        boolean save = save(record);
        if(!save){
            throw new DbException("考试记录保存失败.");
        }
        return true;
    }
}
