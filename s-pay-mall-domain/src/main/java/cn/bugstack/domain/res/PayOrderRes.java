package cn.bugstack.domain.res;

import cn.bugstack.common.constants.Constants;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder//会为每个属性生成
@AllArgsConstructor
@NoArgsConstructor
//支付订单响应
public class PayOrderRes {

    private String userId;
    private String orderId;//订单id
    private String payUrl;
    private Constants.OrderStatusEnum orderStatusEnum;/*枚举订单状态*/

}
