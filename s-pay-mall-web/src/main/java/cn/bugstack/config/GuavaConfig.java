package cn.bugstack.config;

import cn.bugstack.listener.OrderPaySuccessListener;
import cn.bugstack.service.CacheService;
import cn.bugstack.service.impl.GuavaCacheServiceImpl;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.eventbus.EventBus;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
public class GuavaConfig {

    // 定义 weixinAccessTokenCache Bean
    @Bean(name = "weixinAccessTokenCache")
    @ConditionalOnProperty(name = "cache.type", havingValue = "guava")
    public Cache<String, String> weixinAccessTokenCache() {
        return CacheBuilder.newBuilder()
                .expireAfterWrite(2, TimeUnit.HOURS)
                .build();
    }

    // 定义 openidTokenCache Bean
    @Bean(name = "openidTokenCache")
    @ConditionalOnProperty(name = "cache.type", havingValue = "guava")
    public Cache<String, String> openidTokenCache() {
        return CacheBuilder.newBuilder()
                .expireAfterWrite(1, TimeUnit.HOURS)
                .build();
    }

    // 定义 weixinAccessToken (CacheService 类型) Bean，并注入 weixinAccessTokenCache
    @Bean(name = "weixinAccessToken")
    @ConditionalOnProperty(name = "cache.type", havingValue = "guava")
    public CacheService weixinAccessTokenService(@Qualifier("weixinAccessTokenCache") Cache<String, String> cache) {
        return new GuavaCacheServiceImpl(cache);
    }

    // 定义 openidToken (CacheService 类型) Bean，并注入 openidTokenCache
    @Bean(name = "openidToken")
    @ConditionalOnProperty(name = "cache.type", havingValue = "guava")
    public CacheService openidTokenService(@Qualifier("openidTokenCache") Cache<String, String> cache) {
        return new GuavaCacheServiceImpl(cache);
    }

    @Bean
    public EventBus eventBusListener(OrderPaySuccessListener listener){
        EventBus eventBus = new EventBus();
        eventBus.register(listener);
        return eventBus;
    }

}