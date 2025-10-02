package cn.bugstack.config;

import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
public class ThreadPoolConfig implements AsyncConfigurer {

    // 核心线程数：线程池维护的最小线程数，即使空闲也会保留。
    private static final int CORE_POOL_SIZE = 8;
    // 最大线程数：线程池允许创建的最大线程数。
    private static final int MAX_POOL_SIZE = 16;
    // 队列容量：当核心线程都在忙时，新任务会被放入队列等待。
    private static final int QUEUE_CAPACITY = 200;
    // 线程活跃时间：当线程池中的线程数量超过核心线程数时，多余的空闲线程的存活时间。
    private static final int KEEP_ALIVE_SECONDS = 60;
    // 线程名称前缀：方便在日志中识别线程来源。
    private static final String THREAD_NAME_PREFIX = "Async-Service-";

    @Bean("taskExecutor")
    public ThreadPoolTaskExecutor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(CORE_POOL_SIZE);
        executor.setMaxPoolSize(MAX_POOL_SIZE);
        executor.setQueueCapacity(QUEUE_CAPACITY);
        executor.setKeepAliveSeconds(KEEP_ALIVE_SECONDS);
        executor.setThreadNamePrefix(THREAD_NAME_PREFIX);
        // 拒绝策略：当队列和最大线程数都满了时，如何处理新任务。
        // CallerRunsPolicy：由调用者线程处理该任务。
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }

    @Override
    public Executor getAsyncExecutor() {
        return taskExecutor();
    }

    // 异步任务异常统一处理
    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return new AsyncExceptionHandler();
    }
}