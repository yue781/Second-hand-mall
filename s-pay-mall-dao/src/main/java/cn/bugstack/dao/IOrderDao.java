package cn.bugstack.dao;

import cn.bugstack.domain.po.PayOrder;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface IOrderDao {
    /*
    新增订单记录
     */
    void insert(PayOrder payOrder);

    PayOrder queryUnPayOrder(PayOrder payOrder);
    /*
    更新订单支付信息
     */
    void updateOrderPayInfo(PayOrder payOrder);
    /*
    标记订单支付成功
     */
    void changeOrderPaySuccess(PayOrder order);
    /*
    查询未支付且未通知的订单列表
     */
    List<String> queryNoPayNotifyOrder();
    /*
    查询超时未支付的订单号
     */
    List<String> queryTimeoutCloseOrderList();
    /*
    关闭指定订单
     */
    boolean changeOrderClose(String orderId);

}
