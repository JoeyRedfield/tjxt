package com.tianji.learning.controller;

import com.tianji.common.domain.dto.PageDTO;
import com.tianji.learning.domain.dto.ReplyDTO;
import com.tianji.learning.domain.query.ReplyPageQuery;
import com.tianji.learning.domain.vo.ReplyVO;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import com.tianji.learning.service.IInteractionReplyService;
import com.tianji.learning.domain.po.InteractionReply;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.Api;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;

/**
 * <p>
 * 互动问题的回答或评论 控制器
 * </p>
 *
 * @author zywu
 */
@Api(tags = "回答和评论接口 管理")
@RestController
@RequiredArgsConstructor
@RequestMapping("/replies")
public class InteractionReplyController {

    private final IInteractionReplyService interactionReplyService;

    @ApiOperation("在问答的详情页面，用户可以回答问题。也可以对他人的回答和评论做评论")
    @PostMapping
    public void replies(@RequestBody @Validated ReplyDTO replyDTO){
        interactionReplyService.replies(replyDTO);
    }

    @ApiOperation("在问答的详情页面，需要分页查询问题下的回答列表")
    @GetMapping("/page")
    public PageDTO<ReplyVO> repliesPage(ReplyPageQuery query){
        return interactionReplyService.repliesPage(query);
    }


}
