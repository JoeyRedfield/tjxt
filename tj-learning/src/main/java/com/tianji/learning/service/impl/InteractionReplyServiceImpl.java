package com.tianji.learning.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.OrderItem;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tianji.api.client.user.UserClient;
import com.tianji.api.dto.user.UserDTO;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.exceptions.BizIllegalException;
import com.tianji.common.utils.BeanUtils;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.domain.dto.ReplyDTO;
import com.tianji.learning.domain.po.InteractionQuestion;
import com.tianji.learning.domain.po.InteractionReply;
import com.tianji.learning.domain.query.ReplyPageQuery;
import com.tianji.learning.domain.vo.ReplyVO;
import com.tianji.learning.enums.QuestionStatus;
import com.tianji.learning.mapper.InteractionQuestionMapper;
import com.tianji.learning.mapper.InteractionReplyMapper;
import com.tianji.learning.service.IInteractionQuestionService;
import com.tianji.learning.service.IInteractionReplyService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * <p>
 * 互动问题的回答或评论 服务实现类
 * </p>
 *
 * @author zywu
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InteractionReplyServiceImpl extends ServiceImpl<InteractionReplyMapper, InteractionReply> implements IInteractionReplyService {

    private final InteractionQuestionMapper questionMapper;
    private final UserClient userClient;

    @Transactional
    @Override
    public void replies(ReplyDTO replyDTO) {
        if (replyDTO == null) {
            throw new BadRequestException("非法参数");
        }
        // InteractionReply的属性包含了replyDTO所有属性, 除了isStudent
        InteractionReply reply = BeanUtils.copyBean(replyDTO, InteractionReply.class);
        Long userId = UserContext.getUser();
        reply.setUserId(userId);
        // 根据文档, baseMapper.insert()会自动set主键, 而save底层就是insert.
        save(reply); //直接保存就行, 后面再拿id

        // 这是做出回答/评论的用户id, 跟targetUserId不同.

        // 查出对应的问题, 做修改
        InteractionQuestion question = questionMapper.selectOne(new LambdaQueryWrapper<InteractionQuestion>().
                eq(InteractionQuestion::getId, reply.getQuestionId()));

        if (reply.getAnswerId() == null) {
            // reply.getAnswerId()
            // 这1个属性为null, 说明是回答, 而非对回答的评论
            // 是回答就直接插入reply表, 拿到返回值id

            question.setAnswerTimes(question.getAnswerTimes() + 1);
            question.setLatestAnswerId(reply.getId());
        } else {
            // 正常逻辑是要么全都有, 要么全都没有. 全都有的话就是评论, 这时候更新评论
            // 不用更新问题表最近一次回答的id, 因为这是评论.
            Long answerId = replyDTO.getAnswerId(); // 针对回答做出的评论, 这是上级回答id
            InteractionReply answerInfo = getById(answerId);
//            InteractionReply byId = getById(reply.getTargetReplyId());
            // 累加回答下评论的次数
            answerInfo.setReplyTimes(answerInfo.getReplyTimes() + 1);
            this.updateById(answerInfo);
        }

        // 如果是学生回答, 那就设置成"未查看", 老师在后台能看到
        if (replyDTO.getIsStudent()) {
            question.setStatus(QuestionStatus.UN_CHECK);
        } else {
            question.setStatus(QuestionStatus.CHECKED);
        }
        questionMapper.updateById(question);
/*
        // 1.获取登录用户
        Long userId = UserContext.getUser();
        // 2.新增回答
        InteractionReply reply = BeanUtils.toBean(replyDTO, InteractionReply.class);
        reply.setUserId(userId);
        save(reply);
        // 3.累加评论数或者累加回答数
        // 3.1.判断当前回复的类型是否是回答
        boolean isAnswer = replyDTO.getAnswerId() == null;
        if (!isAnswer) {
            // 3.2.是评论，则需要更新上级回答的评论数量
            lambdaUpdate()
                    .setSql("reply_times = reply_times + 1")
                    .eq(InteractionReply::getId, replyDTO.getAnswerId())
                    .update();
        }
        // 3.3.尝试更新问题表中的状态、 最近一次回答、回答数量
        questionService.lambdaUpdate()
                .set(isAnswer, InteractionQuestion::getLatestAnswerId, reply.getAnswerId())
                .setSql(isAnswer, "answer_times = answer_times + 1")
                .set(replyDTO.getIsStudent(), InteractionQuestion::getStatus, QuestionStatus.UN_CHECK.getValue())
                .eq(InteractionQuestion::getId, replyDTO.getQuestionId())
                .update();*/
    }

    @Override
    public PageDTO<ReplyVO> repliesPage(ReplyPageQuery query) {
        if (query == null) {
            throw new BadRequestException("非法参数");
        }
        if (query.getQuestionId() == null && query.getAnswerId() == null) {
            throw new BadRequestException("问题id和回答id不能都为空");
        }

        Page<InteractionReply> replyPage = this.lambdaQuery()
                .eq(query.getQuestionId() != null, InteractionReply::getQuestionId, query.getQuestionId())
//                .eq(query.getAnswerId() != null, InteractionReply::getId, query.getAnswerId())
                .eq(InteractionReply::getAnswerId, query.getAnswerId() == null ? 0L : query.getAnswerId())
                .eq(InteractionReply::getHidden, false)
                .page(query.toMpPage(
                        new OrderItem("liked_times", false),
                        new OrderItem("create_time", true)));
        // todo: 先按点赞数量, 再按创建时间.
//                .page(query.toMpPage("liked_times", false));


        List<InteractionReply> records = replyPage.getRecords();
        if (CollUtils.isEmpty(records)) {
            return PageDTO.empty(replyPage);
        }
        Set<Long> uids = new HashSet<>();
        Set<Long> targetReplyIds = new HashSet<>();

        for (InteractionReply record : records) {
            if (!record.getAnonymity()) {
                uids.add(record.getUserId());
            }
            if (record.getTargetReplyId() != null && record.getTargetReplyId() > 0) {
                // 不为空且大于0, 说明这个是评论
                targetReplyIds.add(record.getTargetReplyId());
            }
        }

        if (!targetReplyIds.isEmpty()) {
            // 说明有评论, 需要获取到评论的用户id, 且要过滤掉匿名
            List<InteractionReply> targetReplies = listByIds(targetReplyIds);
            Set<Long> replyUids = targetReplies.stream().filter(Predicate.not(InteractionReply::getAnonymity))
                    .map(InteractionReply::getUserId).collect(Collectors.toSet());
            uids.addAll(replyUids);
        }

        List<UserDTO> userDTOS = userClient.queryUserByIds(uids);
        Map<Long, UserDTO> userDTOMap = new HashMap<>();
        if (userDTOS != null) {
            userDTOMap = userDTOS.stream().collect(Collectors.toMap(UserDTO::getId, c -> c));
        }

        List<ReplyVO> replyVOList = new ArrayList<>();
        for (InteractionReply record : records) {
            ReplyVO vo = BeanUtils.copyBean(record, ReplyVO.class);
            if (!record.getAnonymity()) {
                UserDTO userDTO = userDTOMap.get(record.getUserId());
                if (userDTO != null) {
                    vo.setUserIcon(userDTO.getIcon());
                    vo.setUserName(userDTO.getName());
                    vo.setUserId(userDTO.getId());
                }
            }
            UserDTO targetUserDTO = userDTOMap.get(record.getTargetUserId());
            if (targetUserDTO != null) {
                vo.setTargetUserName(targetUserDTO.getName());
            }
            replyVOList.add(vo);
        }

        return PageDTO.of(replyPage, replyVOList);
    }

    @Override
    public PageDTO<ReplyVO> repliesPageAdmin(ReplyPageQuery query) {
        if (query == null) {
            throw new BadRequestException("非法参数");
        }
        if (query.getQuestionId() == null && query.getAnswerId() == null) {
            throw new BadRequestException("问题id和回答id不能都为空");
        }

        Page<InteractionReply> replyPage = this.lambdaQuery()
                .eq(query.getQuestionId() != null, InteractionReply::getQuestionId, query.getQuestionId())
//                .eq(query.getAnswerId() != null, InteractionReply::getId, query.getAnswerId())
                .eq(InteractionReply::getAnswerId, query.getAnswerId() == null ? 0L : query.getAnswerId())
                .eq(InteractionReply::getHidden, false)
                .page(query.toMpPage(
                        new OrderItem("liked_times", false),
                        new OrderItem("create_time", true)));

        List<InteractionReply> records = replyPage.getRecords();
        if (CollUtils.isEmpty(records)) {
            return PageDTO.empty(replyPage);
        }

        Set<Long> uids = new HashSet<>();
        Set<Long> targetReplyIds = new HashSet<>();

        for (InteractionReply record : records) {
            uids.add(record.getUserId());
            if (record.getTargetReplyId() != null && record.getTargetReplyId() > 0) {
                // 不为空且大于0, 说明这个是评论
                targetReplyIds.add(record.getTargetReplyId());
            }
        }

        if (!targetReplyIds.isEmpty()) {
            // 说明有评论, 需要获取到评论的用户id, 不用过滤匿名
            List<InteractionReply> targetReplies = listByIds(targetReplyIds);
            Set<Long> replyUids = targetReplies.stream()
                    .map(InteractionReply::getUserId).collect(Collectors.toSet());
            uids.addAll(replyUids);
        }


        List<UserDTO> userDTOS = userClient.queryUserByIds(uids);
        Map<Long, UserDTO> userDTOMap = new HashMap<>();
        if(userDTOS != null){
            userDTOMap = userDTOS.stream().collect(Collectors.toMap(UserDTO::getId, c -> c));
        }

        List<ReplyVO> replyVOList = new ArrayList<>();


        for (InteractionReply record : records) {
            ReplyVO vo = BeanUtils.copyBean(record, ReplyVO.class);
            // 先判断是不是回答
//            UserDTO userDTO = userClient.queryUserById(record.getUserId());
            UserDTO userDTO = userDTOMap.get(record.getUserId());

            if (userDTO != null) {
                vo.setUserName(userDTO.getName());
                vo.setUserIcon(userDTO.getIcon());
                vo.setUserType(userDTO.getType());
            }
//            UserDTO targetUserDTO = userClient.queryUserById(record.getTargetUserId());
            UserDTO targetUserDTO = userDTOMap.get(record.getTargetUserId());
            if (targetUserDTO != null) {
                vo.setTargetUserName(targetUserDTO.getName());
            }
            // TODO
            replyVOList.add(vo);
        }

        return PageDTO.of(replyPage, replyVOList);
    }

    @Override
    @Transactional
    public void repliesHidden(Long id, Boolean hidden) {
        if (id == null || hidden == null) {
            throw new BadRequestException("非法参数");
        }
        InteractionReply reply = getById(id);
        if (reply == null) {
            throw new BizIllegalException("回复/评论不存在");
        }
        // 隐藏回答
        reply.setHidden(hidden);
        updateById(reply);

        // 隐藏评论
        if(reply.getAnswerId() != null && reply.getAnswerId() != 0){
            // 有answerId, 说明是评论, 无需处理s
            return;
        }
        // 能出来, 说明AnswerId为null或者0, 回答默认是0, 这时候就更新对应的AnswerId
        lambdaUpdate()
                .set(InteractionReply::getHidden, hidden)
                .eq(InteractionReply::getAnswerId, id)
                .update();

    }



}
