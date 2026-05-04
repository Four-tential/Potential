package four_tential.potential.infra.async;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@EnableAsync
@Configuration
@Slf4j
public class AsyncConfig {

    @Bean(name = "reviewSummaryExecutor")
    public Executor reviewSummaryExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(3);      // 기본 스레드 수
        executor.setMaxPoolSize(5);       // 최대 스레드 수
        executor.setQueueCapacity(20);    // 대기 큐 크기
        executor.setThreadNamePrefix("review-summary-");
        executor.setRejectedExecutionHandler((r, e) ->
                log.warn("[요약 갱신 큐 초과] 요약 갱신 요청이 거부되었습니다. activeCount={}, queueSize={}",
                        e.getActiveCount(), e.getQueue().size())
        );
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}