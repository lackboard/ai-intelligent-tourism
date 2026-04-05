package com.learn.aiintelligenttourism.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "taskExecutor")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // 核心线程数：根据您的机器性能和 API 速率阈值设定
        executor.setCorePoolSize(10);
        // 最大线程数：遇到高并发时，最多开启多少个线程去处理大模型提取
        executor.setMaxPoolSize(50);
        // 队列容量：如果瞬间来了几千个用户，把提取任务先放在队列里排队，慢慢消化，绝不阻塞！
        executor.setQueueCapacity(2000);
        // 线程名称前缀，方便排查由于大模型提取引发的日志
        executor.setThreadNamePrefix("MemoryLLM-Async-");
        // 拒绝策略：如果几千个用户并发超过了队列容量，丢弃最老的提取任务或者由调用者执行。这里使用 CallerRunsPolicy 或丢弃。
        // 这保证了即使大模型处理不过来，也不会把服务器主业务拖崩。
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
