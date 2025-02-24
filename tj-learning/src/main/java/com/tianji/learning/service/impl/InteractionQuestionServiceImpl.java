package com.tianji.learning.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableFieldInfo;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tianji.api.cache.CategoryCache;
import com.tianji.api.client.course.CatalogueClient;
import com.tianji.api.client.course.CategoryClient;
import com.tianji.api.client.course.CourseClient;
import com.tianji.api.client.search.SearchClient;
import com.tianji.api.client.user.UserClient;
import com.tianji.api.dto.course.*;
import com.tianji.api.dto.user.UserDTO;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.exceptions.BizIllegalException;
import com.tianji.common.utils.BeanUtils;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.StringUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.domain.dto.QuestionFormDTO;
import com.tianji.learning.domain.po.InteractionQuestion;
import com.tianji.learning.domain.po.InteractionReply;
import com.tianji.learning.domain.query.QuestionAdminPageQuery;
import com.tianji.learning.domain.query.QuestionPageQuery;
import com.tianji.learning.domain.vo.QuestionAdminVO;
import com.tianji.learning.domain.vo.QuestionVO;
import com.tianji.learning.enums.QuestionStatus;
import com.tianji.learning.mapper.InteractionQuestionMapper;
import com.tianji.learning.service.IInteractionQuestionService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.learning.service.IInteractionReplyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * <p>
 * 互动提问的问题表 服务实现类
 * </p>
 *
 * @author zywu
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class InteractionQuestionServiceImpl extends ServiceImpl<InteractionQuestionMapper, InteractionQuestion> implements IInteractionQuestionService {

    private final IInteractionReplyService replyService;
    private final UserClient userClient;
    private final SearchClient searchClient;
    private final CourseClient courseClient;
    private final CatalogueClient catalogueClient;
    private final CategoryCache categoryCache;

    @Override
    public void saveQuestion(QuestionFormDTO dto) {
        Long userId = UserContext.getUser();
        InteractionQuestion question = BeanUtils.copyBean(dto, InteractionQuestion.class);
        question.setUserId(userId);

        this.save(question);
    }

    @Override
    public void updateQuestion(Long id, QuestionFormDTO dto) {
        if (StringUtils.isBlank(dto.getTitle()) ||
                StringUtils.isBlank(dto.getDescription()) ||
                dto.getAnonymity() == null) {
            throw new BadRequestException("非法参数");
        }
        InteractionQuestion question = this.getById(id);
        if (question == null) {
            // 相当于做了个校验, 因为是update所以数据库必有数据
            throw new BadRequestException("非法参数");
        }
        if (!Objects.equals(id, UserContext.getUser())) {
            throw new BadRequestException("不能修改别人的互动问题");
        }
        question.setTitle(dto.getTitle());
        question.setDescription(dto.getDescription());
        question.setAnonymity(dto.getAnonymity());

        this.updateById(question);
    }

    @Override
    public PageDTO<QuestionVO> queryQuestionPage(QuestionPageQuery query) {
        if (query.getCourseId() == null) {
            // 小节id可以为空, 这时候默认查询该课程下的所有问答.
            throw new BadRequestException("课程id不能为空");
        }
        // 当前用户id
        Long userId = UserContext.getUser();
        // 根据query和hidden条件查出问题列表
        // 因为是把InteractionQuestion改造成vo对象, 所以留着Page
        Page<InteractionQuestion> questionPage = this.lambdaQuery()
                .select(InteractionQuestion.class, new Predicate<TableFieldInfo>() {
                    @Override
                    public boolean test(TableFieldInfo tableFieldInfo) {
                        // description字段内容太大, 分页可以先排除
                        return !tableFieldInfo.getProperty().equals("description");
                    }
                })
                .eq(InteractionQuestion::getCourseId, query.getCourseId())
                .eq(query.getSectionId() != null, InteractionQuestion::getSectionId, query.getSectionId())
                .eq(query.getOnlyMine(), InteractionQuestion::getUserId, userId) //如果为真, 就设置当前用户id
                .eq(InteractionQuestion::getHidden, false) // 隐藏的问题就没必要查了
                .page(query.toMpPageDefaultSortByCreateTimeDesc());

        List<InteractionQuestion> records = questionPage.getRecords();
        // 记录为空就返回空页.
        if (CollUtils.isEmpty(records)) {
            return PageDTO.empty(questionPage);
        }

        Set<Long> userIds = new HashSet<>();
        Set<Long> questionIds = new HashSet<>();
        // TODO: 优化成根据latest_answer_id去查.
//        Set<Long> latestAnswerIds = new HashSet<>();

        // 根据问题列表, 查出它们的回答, 按时间倒序
        // 查出所有用户的id用于获取name和icon, 合并不适用stream
        for (InteractionQuestion record : records) {
            // 不用再判断hidden, 前面查过了.
            questionIds.add(record.getId());
            // 关于匿名的逻辑可以在最后判断, 是否添加提问者id
            if (!record.getAnonymity()) {
                userIds.add(record.getUserId());
            }
//            if (record.getLatestAnswerId() != null) {
//                latestAnswerIds.add(record.getLatestAnswerId());
//            }
        }

        // 根据问题id和不隐藏条件, 查出所有问题对应的回答

        List<InteractionReply> replyList = replyService.list(Wrappers.<InteractionReply>lambdaQuery()
                .eq(InteractionReply::getHidden, false)
                .in(InteractionReply::getQuestionId, questionIds));
//                .in(InteractionReply::getId, latestAnswerIds));
        // 空replyList的情况也不影响, 单独对replyList做处理就好, userIds为空也是同理.

        // id做key, 方便之后用latest_answer_id
        Map<Long, InteractionReply> replyMap = new HashMap<>();
        replyMap = replyList.stream()
                .collect(Collectors.toMap(InteractionReply::getId, c -> c));

        // 练一下stream收集userId, replyMap和userIds可以合并.
        userIds.addAll(replyList.stream()
                .filter(interactionReply -> !interactionReply.getAnonymity())
                .map(InteractionReply::getUserId).collect(Collectors.toList()));

        // 远程调用服务, 获取用户信息(name和icon)
        // 根据id分好.
        List<UserDTO> userDTOS = userClient.queryUserByIds(userIds);
        Map<Long, UserDTO> userDTOMap = userDTOS.stream().collect(Collectors.toMap(UserDTO::getId, c -> c));

        // 封装vo对象
        List<QuestionVO> voList = new ArrayList<>();
        for (InteractionQuestion record : records) {
            QuestionVO vo = BeanUtils.copyBean(record, QuestionVO.class);
            if (!vo.getAnonymity()) {
                Long userVOId = vo.getUserId();
                // 如果userVOId不为空, 那么根据userIds生成的userDTOMap就能查到.
                UserDTO userDTO = userDTOMap.get(userVOId);
                if (userDTO != null) {
                    vo.setUserName(userDTO.getName()); // 1
                    vo.setUserIcon(userDTO.getIcon());
                    vo.setUserId(userDTO.getId()); // 2
                }
            }

            Long latestAnswerId = record.getLatestAnswerId();
            if (latestAnswerId != null) {
                // latestAnswerId不为空, 一般来说reply就有记录
                InteractionReply reply = replyMap.get(latestAnswerId);
                if (reply != null) {
                    if (!reply.getAnonymity()) {
                        UserDTO userDTO = userDTOMap.get(reply.getUserId());
                        vo.setLatestReplyUser(userDTO.getName()); // 3
                    }
                    vo.setLatestReplyContent(reply.getContent()); // 4
                }
            }
            voList.add(vo);
        }

        return PageDTO.of(questionPage, voList);
    }

    @Override
    public QuestionVO queryQuestionById(Long id) {
        if (id == null) {
            throw new BadRequestException("非法参数");
        }
        InteractionQuestion question = getById(id);
        if (question == null) {
            throw new BadRequestException("问题不存在");
        }
        if (question.getHidden()) {
            return null;
        }
        QuestionVO vo = BeanUtils.copyBean(question, QuestionVO.class);
        if (!question.getAnonymity()) {
            UserDTO userDTO = userClient.queryUserById(question.getUserId());
            if (userDTO != null) {
                vo.setUserIcon(userDTO.getIcon());
                vo.setUserName(userDTO.getName());
            }
        }
        return vo;
    }

    @Override
    public PageDTO<QuestionAdminVO> queryQuestionAdminVOPage(QuestionAdminPageQuery query) {
        String courseName = query.getCourseName();
        List<Long> cids = null;

        if (StringUtils.isNotBlank(courseName)) {
            cids = searchClient.queryCoursesIdByName(courseName);
            if (CollUtils.isEmpty(cids)) {
                return PageDTO.empty(0L, 0L);
            }
        }

        // 根据课程名称, 调用es服务查出匹配的courseIds
        Page<InteractionQuestion> questionPage = this.lambdaQuery()
                .in(CollUtils.isNotEmpty(cids), InteractionQuestion::getCourseId, cids)
                .eq(query.getStatus() != null, InteractionQuestion::getStatus, query.getStatus())
                .gt(query.getBeginTime() != null, InteractionQuestion::getCreateTime, query.getBeginTime())
                .lt(query.getEndTime() != null, InteractionQuestion::getCreateTime, query.getEndTime())
                .page(query.toMpPageDefaultSortByCreateTimeDesc());

        List<InteractionQuestion> records = questionPage.getRecords();
        if (CollUtils.isEmpty(records)) {
            return PageDTO.empty(0L, 0L);
        }
        // 课程id
        Set<Long> courseIds = new HashSet<>();
        Set<Long> userIds = new HashSet<>();
        Set<Long> chapterAndSectionIds = new HashSet<>();

        for (InteractionQuestion record : records) {
            courseIds.add(record.getCourseId());
            userIds.add(record.getUserId());
            chapterAndSectionIds.add(record.getChapterId());
            chapterAndSectionIds.add(record.getSectionId());
        }
        List<UserDTO> userDTOS = userClient.queryUserByIds(userIds);
        if (CollUtils.isEmpty(userDTOS)) {
            throw new BizIllegalException("用户不存在");
        }
        Map<Long, String> userMaps = userDTOS.stream().collect(Collectors.toMap(UserDTO::getId, UserDTO::getName));

        List<CataSimpleInfoDTO> cataList = catalogueClient.batchQueryCatalogue(chapterAndSectionIds);
        if (CollUtils.isEmpty(cataList)) {
            throw new BizIllegalException("章.节信息不存在");
        }
        Map<Long, String> cataMaps = cataList.stream().collect(Collectors.toMap(CataSimpleInfoDTO::getId, CataSimpleInfoDTO::getName));

        List<CourseSimpleInfoDTO> cinfoList = courseClient.getSimpleInfoList(courseIds);
        Map<Long, CourseSimpleInfoDTO> cinfoMap = cinfoList.stream().collect(Collectors.toMap(CourseSimpleInfoDTO::getId, c -> c));


        List<QuestionAdminVO> voList = new ArrayList<>();
        for (InteractionQuestion record : records) {
            QuestionAdminVO vo = BeanUtils.copyBean(record, QuestionAdminVO.class);
            // userMaps就是用record的userId去查的数据库再加stream转换的,
            // 前面也做了userDTOS判空, 所以这里没必要设置. 即使get到了null也不影响.
            String userName = userMaps.get(record.getUserId());
            vo.setUserName(userName);

            CourseSimpleInfoDTO cdto = cinfoMap.get(record.getCourseId());
            if (cdto != null) {
                vo.setCourseName(cdto.getName());
                vo.setCategoryName(categoryCache.getCategoryNames(cdto.getCategoryIds()));
            }

            vo.setChapterName(cataMaps.get(record.getChapterId()));
            vo.setSectionName(cataMaps.get(record.getSectionId()));

            voList.add(vo);
        }

        return PageDTO.of(questionPage, voList);
    }

    /**
     * @param id 问题id
     */
    @Override
    public void deleteMyQuestion(Long id) {
        if (id == null) {
            throw new BadRequestException("非法参数");
        }
        InteractionQuestion question = this.lambdaQuery().eq(InteractionQuestion::getId, id).one();
        if (question == null) {
            throw new BizIllegalException("问题不存在");
        }
        Long userId = UserContext.getUser();
        if (!Objects.equals(userId, question.getUserId())) {
            throw new BizIllegalException("不是当前用户提问的问题");
        }
        this.removeById(question);
        // 删除问题下的回答以及评论
        replyService.remove(new LambdaQueryWrapper<InteractionReply>()
                .eq(InteractionReply::getQuestionId, question.getId()));
    }

    @Override
    public void hideQuestionAdmin(Long id, Boolean hidden) {
        if (id == null || hidden == null) {
            throw new BadRequestException("非法参数");
        }
        InteractionQuestion question = this.lambdaQuery().eq(InteractionQuestion::getId, id).one();
        if (question == null) {
            throw new BizIllegalException("问题不存在");
        }
        question.setHidden(hidden);
        updateById(question);
    }

    @Override
    public QuestionAdminVO queryQuestionAdminVOById(Long id) {
        if (id == null) {
            throw new BadRequestException("非法参数");
        }
        InteractionQuestion question = getById(id);
        if (question == null) {
            throw new BizIllegalException("问题不存在");
        }
        if(question.getStatus().equals(QuestionStatus.UN_CHECK)){
            // 如果是未查看, 调用接口就设置成已经查看, 并更新
            question.setStatus(QuestionStatus.CHECKED);
            updateById(question);
        }

        UserDTO userDTO = userClient.queryUserById(question.getUserId());
        if (userDTO == null) {
            throw new BizIllegalException("用户不存在");
        }

//        CourseFullInfoDTO courseInfoById = courseClient.getCourseInfoById(question.getCourseId(), true, true);
//        if(courseInfoById == null){
//            throw new BizIllegalException("课程不存在");
//        }

        CourseSearchDTO courseSearchDTO = courseClient.getSearchInfo(question.getCourseId());
        if(courseSearchDTO == null){
            throw new BizIllegalException("课程不存在");
        }

        Long teacherId = courseSearchDTO.getTeacher();
        UserDTO teacherDTO = userClient.queryUserById(teacherId);
        Set<Long> chapterAndSectionIds = new HashSet<>();
        // 查找章节
        chapterAndSectionIds.add(question.getChapterId());
        chapterAndSectionIds.add(question.getSectionId());

        List<CataSimpleInfoDTO> cataList = catalogueClient.batchQueryCatalogue(chapterAndSectionIds);
        if (CollUtils.isEmpty(cataList)) {
            throw new BizIllegalException("章.节信息不存在");
        }
        Map<Long, String> cataMaps = cataList.stream().collect(Collectors.toMap(CataSimpleInfoDTO::getId, CataSimpleInfoDTO::getName));


        QuestionAdminVO vo = BeanUtils.copyBean(question, QuestionAdminVO.class);
        vo.setUserName(userDTO.getName());
        vo.setUserIcon(userDTO.getIcon());
        vo.setUserId(userDTO.getId());

        vo.setCourseName(courseSearchDTO.getName());
        vo.setChapterName(cataMaps.get(question.getChapterId()));
        vo.setSectionName(cataMaps.get(question.getSectionId()));

        vo.setCategoryName(categoryCache.getCategoryNames(
                List.of(courseSearchDTO.getCategoryIdLv1(),
                        courseSearchDTO.getCategoryIdLv2(),
                        courseSearchDTO.getCategoryIdLv3())
        ));

        vo.setTeacherName(teacherDTO.getName());

        return vo;
    }

}
