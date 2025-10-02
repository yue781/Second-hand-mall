package cn.bugstack.service.impl;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import java.util.Random;
import java.util.concurrent.TimeUnit;

@Service
public class ProductStockService {

    @Autowired
    private StringRedisTemplate redisTemplate;

    private static final String PRODUCT_STOCK_PREFIX = "product:stock:";
    private static final int SHARD_COUNT = 10; // 库存分片数量，可配置
    private Random random = new Random();

    /**
     * 初始化商品库存到分片
     * @param productId 商品ID
     * @param stock 总库存量
     */
    public void initProductStock(Long productId, int stock) {
        int stockPerShard = stock / SHARD_COUNT;
        for (int i = 0; i < SHARD_COUNT; i++) {
            redisTemplate.opsForValue().set(getProductStockKey(productId, i), String.valueOf(stockPerShard));
        }
        // 处理余数
        if (stock % SHARD_COUNT > 0) {
            redisTemplate.opsForValue().increment(getProductStockKey(productId, 0), stock % SHARD_COUNT);
        }
    }

    /**
     * 扣减商品库存
     * @param productId 商品ID
     * @param num 扣减数量
     * @return 是否扣减成功
     */
    public boolean deductStock(Long productId, int num) {
        // 尝试随机分片扣减
        for (int i = 0; i < SHARD_COUNT; i++) {
            int shardId = random.nextInt(SHARD_COUNT); // 随机选择一个分片
            String key = getProductStockKey(productId, shardId);
            Long currentStock = redisTemplate.opsForValue().increment(key, -num);
            if (currentStock != null && currentStock >= 0) {
                // 扣减成功
                return true;
            } else if (currentStock != null) {
                // 扣减失败，回滚当前分片
                redisTemplate.opsForValue().increment(key, num);
            }
        }
        // 如果所有分片都尝试失败，则表示库存不足
        return false;
    }

    /**
     * 查询商品总库存
     * @param productId 商品ID
     * @return 总库存量
     */
    public int getProductTotalStock(Long productId) {
        int totalStock = 0;
        for (int i = 0; i < SHARD_COUNT; i++) {
            String key = getProductStockKey(productId, i);
            String stockStr = redisTemplate.opsForValue().get(key);
            if (stockStr != null) {
                totalStock += Integer.parseInt(stockStr);
            }
        }
        return totalStock;
    }

    private String getProductStockKey(Long productId, int shardId) {
        return PRODUCT_STOCK_PREFIX + productId + ":" + shardId;
    }
}