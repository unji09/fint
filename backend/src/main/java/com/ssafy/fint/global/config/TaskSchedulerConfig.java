package com.ssafy.fint.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * 자연어 쿼리 SSE 의 Redis polling 등 주기 작업용 스케줄러.
 * 한 SSE 연결당 polling 1건이 실행되며 Future.cancel 로 즉시 정리된다.
 */
@Configuration
public class TaskSchedulerConfig {

    @Bean
    public TaskScheduler dashboardPollingScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(4);
        scheduler.setThreadNamePrefix("dashboard-poll-");
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(5);
        scheduler.initialize();
        return scheduler;
    }
}
