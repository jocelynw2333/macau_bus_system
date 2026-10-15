package com.albez.bus.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

import java.util.concurrent.Executor;

@Configuration
public class SchedulingConfig implements SchedulingConfigurer {
    @Value("${bus.scheduler.pool-size:4}")
    private int schedulerPoolSize;

    @Value("${bus.refresh-executor.pool-size:4}")
    private int refreshExecutorPoolSize;

    @Bean(destroyMethod = "shutdown")
    public ThreadPoolTaskScheduler taskScheduler() {
        int poolSize = Math.max(4, schedulerPoolSize);
        ThreadPoolTaskScheduler taskScheduler = new ThreadPoolTaskScheduler();
        taskScheduler.setPoolSize(poolSize);
        taskScheduler.setThreadNamePrefix("scheduled-task-");
        taskScheduler.setDaemon(true);
        taskScheduler.setWaitForTasksToCompleteOnShutdown(false);
        return taskScheduler;
    }

    @Bean(destroyMethod = "shutdown")
    public Executor refreshTaskExecutor() {
        int poolSize = Math.max(4, refreshExecutorPoolSize);
        ThreadPoolTaskExecutor taskExecutor = new ThreadPoolTaskExecutor();
        taskExecutor.setCorePoolSize(poolSize);
        taskExecutor.setMaxPoolSize(poolSize);
        taskExecutor.setQueueCapacity(100);
        taskExecutor.setThreadNamePrefix("refresh-task-");
        taskExecutor.setDaemon(true);
        taskExecutor.setWaitForTasksToCompleteOnShutdown(false);
        return taskExecutor;
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
        taskRegistrar.setTaskScheduler(taskScheduler());
    }
}
