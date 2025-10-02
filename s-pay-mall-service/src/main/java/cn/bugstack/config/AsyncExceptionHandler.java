package cn.bugstack.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import java.lang.reflect.Method;

public class AsyncExceptionHandler implements AsyncUncaughtExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(AsyncExceptionHandler.class);

    @Override
    public void handleUncaughtException(Throwable ex, Method method, Object... params) {
        logger.error("Async method has uncaught exception, method: " + method.getName() + ", params: " + String.join(", ", (CharSequence[]) params), ex);
        // 可以在这里添加更复杂的异常处理逻辑，例如发送告警邮件、记录到数据库等
    }
}