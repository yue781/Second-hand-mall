package cn.bugstack.service.impl;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class HotProductService {

    @Autowired
    private StringRedisTemplate redisTemplate;
    // 假设这是商品数据访问层或服务
    // @Autowired
    // private ProductMapper productMapper;

    private static final String HOT_PRODUCTS_KEY = "hot_products";
    private static final String PRODUCT_SALES_PREFIX = "product:sales:";
    private static final String PRODUCT_CLICKS_PREFIX = "product:clicks:";

    /**
     * 定时任务：每小时更新一次热门商品列表
     * 实际应用中，可以根据业务需求调整 cron 表达式
     */
    @Scheduled(fixedRate = 3600000) // 每小时执行一次 (毫秒)
    public void refreshHotProducts() {
        // 1. 从数据库或数据源获取所有商品ID
        // List<Long> allProductIds = productMapper.getAllProductIds();
        // 模拟获取商品ID
        List<Long> allProductIds = List.of(1001L, 1002L, 1003L, 1004L, 1005L);

        // 2. 获取每个商品的销量和点击量
        Map<Long, Double> productScores = allProductIds.stream().collect(Collectors.toMap(
                productId -> productId,
                productId -> {
                    Double sales = getProductSales(productId);
                    Double clicks = getProductClicks(productId);
                    // 可以根据业务需求调整权重，例如销量权重更高
                    return sales * 0.7 + clicks * 0.3;
                }
        ));

        // 3. 根据综合分数排序，选出Top N热门商品
        List<Long> hotProductIds = productScores.entrySet().stream()
                .sorted(Map.Entry.<Long, Double>comparingByValue().reversed())
                .limit(10) // 假设取Top 10热门商品
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        // 4. 将热门商品ID列表缓存到Redis，并设置过期时间
        redisTemplate.opsForValue().set(HOT_PRODUCTS_KEY, hotProductIds.toString(), 1, TimeUnit.HOURS);
        System.out.println("热门商品列表已更新: " + hotProductIds);
    }

    /**
     * 获取商品销量 (从 Redis 或数据库获取)
     * 实际中，销量可以在订单支付成功后异步更新到 Redis 或数据库
     */
    private Double getProductSales(Long productId) {
        String salesStr = redisTemplate.opsForValue().get(PRODUCT_SALES_PREFIX + productId);
        return salesStr == null ? 0.0 : Double.parseDouble(salesStr);
    }

    /**
     * 获取商品点击量 (从 Redis 或数据库获取)
     * 实际中，点击量可以在用户访问商品详情页时异步埋点并更新到 Redis 或数据库
     */
    private Double getProductClicks(Long productId) {
        String clicksStr = redisTemplate.opsForValue().get(PRODUCT_CLICKS_PREFIX + productId);
        return clicksStr == null ? 0.0 : Double.parseDouble(clicksStr);
    }

    /**
     * 获取热门商品列表
     */
    public List<Long> getHotProducts() {
        String hotProductsStr = redisTemplate.opsForValue().get(HOT_PRODUCTS_KEY);
        if (hotProductsStr != null) {
            // 简单解析字符串，实际中可能需要更健壮的JSON解析
            return List.of(hotProductsStr.substring(1, hotProductsStr.length() - 1).split(", "))
                    .stream().map(Long::parseLong).collect(Collectors.toList());
        }
        return List.of();
    }
}