package com.tianji.learning.controller;

import com.tianji.common.domain.dto.PageDTO;
import com.tianji.learning.domain.dto.ReplyDTO;
import com.tianji.learning.domain.query.ReplyPageQuery;
import com.tianji.learning.domain.vo.ReplyVO;
import com.tianji.learning.service.IInteractionReplyService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * <p>
 * 互动问题的回答或评论 控制器
 * </p>
 *
 * @author zywu
 */
@Api(tags = "回答和评论接口 管理 - 管理端")
@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/replies")
public class InteractionReplyAdminController {

    private final IInteractionReplyService interactionReplyService;

    @ApiOperation("在问答的详情页面，需要分页查询问题下的回答列表")
    @GetMapping("/page")
    public PageDTO<ReplyVO> repliesPageAdmin(ReplyPageQuery query){
        return interactionReplyService.repliesPageAdmin(query);
    }

    @ApiOperation("显示或隐藏评论 - 管理端")
    @PutMapping("/{id}/hidden/{hidden}")
    public void repliesHidden(@PathVariable("id") Long id,
                              @PathVariable("hidden") Boolean hidden){
        interactionReplyService.repliesHidden(id, hidden);

    }


}
