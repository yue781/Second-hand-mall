package cn.bugstack.service.impl;
import cn.bugstack.common.constants.Constants;
import cn.bugstack.dao.IOrderDao;
import cn.bugstack.domain.po.PayOrder;
import cn.bugstack.domain.req.ShopCartReq;
import cn.bugstack.domain.res.PayOrderRes;
import cn.bugstack.domain.vo.ProductVO;
import cn.bugstack.service.IOrderService;
import cn.bugstack.service.rpc.ProductRPC;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.request.AlipayTradePagePayRequest;
import com.google.common.eventbus.EventBus;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

import org.springframework.scheduling.annotation.Async;

/**
 * @author Fuzhengwei bugstack.cn @小傅哥
 * @description 订单服务
 * @create 2024-09-29 09:47
 */
@Slf4j
@Service
public class OrderServiceImpl implements IOrderService {

    @Value("${alipay.notify_url}")
    private String notifyUrl;
    @Value("${alipay.return_url}")
    private String returnUrl;

    @Resource
    private IOrderDao orderDao;
    @Resource
    private ProductRPC productRPC;
    @Resource
    private AlipayClient alipayClient;

    @Resource
    private EventBus eventBus;
    @Resource
    private RabbitTemplate rabbitTemplate;

    @Override
    public PayOrderRes createOrder(ShopCartReq shopCartReq) throws Exception {
        // 1. 查询当前用户是否存在未支付订单或掉单订单
        PayOrder payOrderReq = new PayOrder();
        payOrderReq.setUserId(shopCartReq.getUserId());
        payOrderReq.setProductId(shopCartReq.getProductId());
        //判断是否存在未支付的订单
        PayOrder unpaidOrder = orderDao.queryUnPayOrder(payOrderReq);

        if (null != unpaidOrder && Constants.OrderStatusEnum.PAY_WAIT.getCode().equals(unpaidOrder.getStatus())) {
            log.info("创建订单-存在，已存在未支付订单。userId:{} productId:{} orderId:{}", shopCartReq.getUserId(), shopCartReq.getProductId(), unpaidOrder.getOrderId());
            return PayOrderRes.builder()//PayOrderRes.builder()创建了一个PayOrderRes类的对象，相当于PayOrderRes res=new PayOrderRes()
                    .orderId(unpaidOrder.getOrderId())
                    .payUrl(unpaidOrder.getPayUrl())
                    .build();//调用 build() 方法时，建造者会将所有设置的属性组装成一个完整的 PayOrderRes 对象。
        } else if (null != unpaidOrder && Constants.OrderStatusEnum.CREATE.getCode().equals(unpaidOrder.getStatus())) {
            log.info("创建订单-存在，存在未创建支付单订单，创建支付单开始 userId:{} productId:{} orderId:{}", shopCartReq.getUserId(), shopCartReq.getProductId(), unpaidOrder.getOrderId());
            PayOrder payOrder = doPrepayOrder(unpaidOrder.getProductId(), unpaidOrder.getProductName(), unpaidOrder.getOrderId(), unpaidOrder.getTotalAmount());
            return PayOrderRes.builder()
                    .orderId(payOrder.getOrderId())
                    .payUrl(payOrder.getPayUrl())
                    .build();
        }

        // 2. 查询商品 & 创建订单
        ProductVO productVO = productRPC.queryProductByProductId(shopCartReq.getProductId());
        String orderId = RandomStringUtils.randomNumeric(16);
        PayOrder newOrder = PayOrder.builder()
                .userId(shopCartReq.getUserId())
                .productId(shopCartReq.getProductId())
                .productName(productVO.getProductName())
                .orderId(orderId)
                .totalAmount(productVO.getPrice())
                .orderTime(new Date())
                .status(Constants.OrderStatusEnum.CREATE.getCode())
                .build();
        orderDao.insert(newOrder);

        // 3. 创建支付单
        PayOrder payOrder = doPrepayOrder(productVO.getProductId(), productVO.getProductName(), orderId, productVO.getPrice());

        // 4. 发送延迟消息到 RabbitMQ，用于未支付订单自动关闭
        // 假设延迟时间为 30 分钟 (30 * 60 * 1000 毫秒)
        // 这里的交换机和路由键需要与 RabbitMQConfig 中定义的保持一致
        // Constants.RABBITMQ_ORDER_EXCHANGE 和 Constants.RABBITMQ_ORDER_DELAY_ROUTING_KEY 需要在 Constants 类中定义
        // 消息内容可以是你需要传递的订单信息，例如订单ID
        log.info("发送延迟消息：订单ID {}，延迟时间 {} 毫秒", orderId, Constants.ORDER_DELAY_TIME_MILLIS);
        rabbitTemplate.convertAndSend(Constants.RABBITMQ_ORDER_EXCHANGE,
                Constants.RABBITMQ_ORDER_DELAY_ROUTING_KEY,
                orderId, // 消息内容，这里传递订单ID
                message -> {
                    message.getMessageProperties().setExpiration(String.valueOf(Constants.ORDER_DELAY_TIME_MILLIS));
                    return message;
                });

        return PayOrderRes.builder()
                .orderId(orderId)
                .payUrl(payOrder.getPayUrl())
                .build();
    }
    //订单支付成功处理
    @Override
    public void changeOrderPaySuccess(String orderId) {
        PayOrder payOrderReq = new PayOrder();
        payOrderReq.setOrderId(orderId);
        //修改订单状态
        payOrderReq.setStatus(Constants.OrderStatusEnum.PAY_SUCCESS.getCode());
        orderDao.changeOrderPaySuccess(payOrderReq);
        //通过eventbus发送订单变更事件
        eventBus.post(JSON.toJSONString(payOrderReq));

        // 异步处理非核心业务
        // 假设从订单中获取商品ID和购买数量
        Long productId = Long.valueOf(payOrderReq.getProductId()); // 需要根据实际情况获取商品ID
        int quantity = 1; // 需要根据实际情况获取购买数量
        processOrderPaid(orderId, productId, quantity);
    }

    @Override
    public List<String> queryNoPayNotifyOrder() {
        return orderDao.queryNoPayNotifyOrder();
    }

    @Override
    public List<String> queryTimeoutCloseOrderList() {
        return orderDao.queryTimeoutCloseOrderList();
    }
    //关闭指定订单
    @Override
    public boolean changeOrderClose(String orderId) {
        return orderDao.changeOrderClose(orderId);
    }
    //生成预支付信息
    private PayOrder doPrepayOrder(String productId, String productName, String orderId, BigDecimal totalAmount) throws AlipayApiException {
        //创建支付宝请求
        AlipayTradePagePayRequest request = new AlipayTradePagePayRequest();
        //设置回调异步通知url和同步跳转url
        request.setNotifyUrl(notifyUrl);
        request.setReturnUrl(returnUrl);

        JSONObject bizContent = new JSONObject();
        bizContent.put("out_trade_no", orderId);
        bizContent.put("total_amount", totalAmount.toString());
        bizContent.put("subject", productName);
        bizContent.put("product_code", "FAST_INSTANT_TRADE_PAY");
        request.setBizContent(bizContent.toString());

        String form = alipayClient.pageExecute(request).getBody();

        PayOrder payOrder = new PayOrder();
        payOrder.setOrderId(orderId);
        payOrder.setPayUrl(form);
        payOrder.setStatus(Constants.OrderStatusEnum.PAY_WAIT.getCode());
        //更新订单支付信息
        orderDao.updateOrderPayInfo(payOrder);

        return payOrder;
    }

    /**
     * 模拟订单支付成功后的处理逻辑，触发异步任务
     * @param orderId 订单ID
     * @param productId 商品ID
     * @param quantity 购买数量
     */
    public void processOrderPaid(String orderId, Long productId, int quantity) {
        log.info("订单 {} 支付成功，开始处理核心业务...", orderId);
        // 核心业务逻辑，例如：更新订单状态、生成发货单等

        // 异步处理非核心业务
        updateProductSalesAsync(productId, quantity);
        recordProductClickAsync(productId);

        log.info("订单 {} 核心业务处理完成，非核心业务已异步触发。", orderId);
    }

    /**
     * 异步更新商品销量
     * 使用 @Async 注解，该方法将在独立的线程池中执行
     * @param productId 商品ID
     * @param quantity 购买数量
     */
    @Async("taskExecutor") // 指定使用名为 "taskExecutor" 的线程池
    public void updateProductSalesAsync(Long productId, int quantity) {
        try {
            log.info("异步任务：开始更新商品 {} 销量，数量 {}。当前线程: {}", productId, quantity, Thread.currentThread().getName());
            // 模拟耗时操作，例如更新数据库或 Redis 销量统计
            Thread.sleep(100);
            // productService.updateSales(productId, quantity);
            log.info("异步任务：商品 {} 销量更新完成。", productId);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("异步更新商品销量中断", e);
        } catch (Exception e) {
            log.error("异步更新商品销量失败", e);
        }
    }

    /**
     * 异步记录商品点击量
     * @param productId 商品ID
     */
    @Async("taskExecutor")
    public void recordProductClickAsync(Long productId) {
        try {
            log.info("异步任务：开始记录商品 {} 点击量。当前线程: {}", productId, Thread.currentThread().getName());
            // 模拟耗时操作，例如更新数据库或 Redis 点击量统计
            Thread.sleep(50);
            // productService.recordClick(productId);
            log.info("异步任务：商品 {} 点击量记录完成。", productId);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("异步记录商品点击量中断", e);
        } catch (Exception e) {
            log.error("异步记录商品点击量失败", e);
        }
    }

}