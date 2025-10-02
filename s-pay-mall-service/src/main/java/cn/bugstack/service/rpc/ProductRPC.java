package cn.bugstack.service.rpc;

import cn.bugstack.domain.vo.ProductVO;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
//ProductRPC相当于服务员，在客户端和服务端之间传递信息
public class ProductRPC {
    //以下代码由服务端完成
    public ProductVO queryProductByProductId(String productId){
        ProductVO productVO = new ProductVO();
        productVO.setProductId(productId);
        productVO.setProductName("测试商品");
        productVO.setProductDesc("这是一个测试商品");
        productVO.setPrice(new BigDecimal("1.68"));
        return productVO;
    }
}
