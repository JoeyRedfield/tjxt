package com.tianji.learning.mq;

import com.tianji.api.dto.trade.OrderBasicDTO;
import com.tianji.common.constants.MqConstants;

import com.tianji.common.utils.CollUtils;
import com.tianji.learning.service.ILearningLessonService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor // 带参数构造器, lombok是在编译期生成相应的方法.
public class LessonChangeListener {

    final ILearningLessonService learningService;

//    public LessonChangeListener(ILearningLessonService learningService) {
//        this.learningService = learningService;
//    }

    @RabbitListener(bindings = @QueueBinding(value = @Queue(value = "learning.lesson.pay.queue", durable = "true"),
            exchange = @Exchange(value = MqConstants.Exchange.ORDER_EXCHANGE, type = ExchangeTypes.TOPIC),
            key = MqConstants.Key.ORDER_PAY_KEY))
    public void onMsg(OrderBasicDTO dto) {
        log.info("LessonChangeListener 接收到了消息 用户{} 添加课程{}", dto.getUserId(), dto.getCourseIds());
        if (dto.getUserId() == null
                || dto.getOrderId() == null
                || CollUtils.isEmpty(dto.getCourseIds())) {
            // 不要抛异常, 否则会开启重试.
            return;
        }
        learningService.addUserLesson(dto.getUserId(), dto.getCourseIds());
    }

    @RabbitListener(bindings = @QueueBinding(value = @Queue(value = "learning.lesson.refund.queue", durable = "true"),
            exchange = @Exchange(value = MqConstants.Exchange.ORDER_EXCHANGE, type = ExchangeTypes.TOPIC),
            key = MqConstants.Key.ORDER_REFUND_KEY))
    public void onRefund(OrderBasicDTO dto) {
//        删除课表中的课程有两种场景：
//        - 用户直接删除已失效的课程
//                - 用户退款后触发课表自动删除
        log.info("LessonChangeListener 接收到了消息 用户{} 删除的课程id{}", dto.getUserId(), dto.getCourseIds());
        if (dto.getUserId() == null
                || dto.getOrderId() == null
                || CollUtils.isEmpty(dto.getCourseIds())) {
            // 不要抛异常, 否则会开启重试.
            return;
        }
        learningService.deleteLearningLessons(dto.getCourseIds());
    }

}
