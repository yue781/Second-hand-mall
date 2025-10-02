package cn.bugstack.service.listener;

import cn.bugstack.common.constants.Constants;
import cn.bugstack.service.IOrderService;
import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.io.IOException;

/**
 * @author Fuzhengwei bugstack.cn @小傅哥
 * @description 订单关闭消息监听器
 * @create 2024-09-29 09:47
 */
@Slf4j
@Component
public class OrderCloseMessageListener {

    @Resource
    private IOrderService orderService;

    /**
     * 监听订单关闭死信队列
     * 当延迟队列中的消息过期后，会被路由到死信交换机，然后进入死信队列 order.close.queue
     *
     * @param orderId 订单ID，从消息体中获取
     * @param channel RabbitMQ 通道
     * @param message 原始消息对象
     * @throws IOException
     */
    @RabbitListener(queues = {Constants.RABBITMQ_ORDER_CLOSE_QUEUE})
    public void handleOrderCloseMessage(String orderId, Channel channel, Message message) throws IOException {
        log.info("收到订单关闭消息，订单ID: {}", orderId);
        long deliveryTag = message.getMessageProperties().getDeliveryTag();

        try {
            // 调用订单服务执行关闭订单逻辑
            boolean closed = orderService.changeOrderClose(orderId);

            if (closed) {
                log.info("订单 {} 关闭成功", orderId);
                // 手动确认消息，表示消息已成功处理
                channel.basicAck(deliveryTag, false);
            } else {
                log.warn("订单 {} 关闭失败或已处理", orderId);
                // 订单可能已经被支付或已关闭，不需要重复处理，也进行确认
                channel.basicAck(deliveryTag, false);
            }
        } catch (Exception e) {
            log.error("处理订单关闭消息异常，订单ID: {}", orderId, e);
            // 处理失败，可以选择拒绝消息并重新入队 (requeue = true) 或丢弃 (requeue = false)
            // 为了避免无限重试导致队列阻塞，通常建议记录日志并丢弃或发送到另一个错误处理队列
            // 这里选择拒绝消息但不重新入队
            channel.basicReject(deliveryTag, false);
        }
    }
}