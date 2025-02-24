package com.tianji.learning.controller;

import com.tianji.common.domain.dto.PageDTO;
import com.tianji.learning.domain.dto.QuestionFormDTO;
import com.tianji.learning.domain.query.QuestionPageQuery;
import com.tianji.learning.domain.vo.QuestionVO;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import com.tianji.learning.service.IInteractionQuestionService;
import com.tianji.learning.domain.po.InteractionQuestion;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.Api;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;

/**
 * <p>
 * 互动提问的问题表 控制器
 * </p>
 *
 * @author zywu
 */
@Api(tags = "互动问题 相关接口")
@RestController
@RequiredArgsConstructor
@RequestMapping("/questions")
public class InteractionQuestionController {

    private final IInteractionQuestionService questionService;

    @ApiOperation("新增互动问题")
    @PostMapping
    public void saveQuestion(@Validated @RequestBody QuestionFormDTO dto){
        questionService.saveQuestion(dto);
    }

    @ApiOperation("修改互动问题")
    @PutMapping("/{id}")
    public void updateQuestion(@PathVariable Long id, @RequestBody QuestionFormDTO dto){
        // 这里一般不用加@Validated, 看前端传什么东西.
        questionService.updateQuestion(id, dto);
    }

    @ApiOperation("分页查询互动问题-用户端")
    @GetMapping("/page")
    public PageDTO<QuestionVO> queryQuestionPage(QuestionPageQuery query){
        return questionService.queryQuestionPage(query);
    }

    @ApiOperation("查询问题详情-用户端")
    @GetMapping("/{id}")
    public QuestionVO queryQuestionById(@PathVariable Long id){
        return questionService.queryQuestionById(id);
    }

    @ApiOperation("删除问题 - 用户端")
    @DeleteMapping("/{id}")
    public void deleteMyQuestion(@PathVariable Long id){
        questionService.deleteMyQuestion(id);
    }

}
