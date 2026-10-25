package com.testhub.apiexec.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Bounded worker pool backing asynchronous task execution. */
@Configuration
public class ExecutorConfig {

    public static final String EXECUTOR_BEAN = "apiExecutionExecutor";

    /**
     * Exactly {@code testhub.apiexec.workers} threads and one FIFO
     * {@link LinkedBlockingQueue}; tasks therefore start in creation order.
     */
    @Bean(name = EXECUTOR_BEAN, destroyMethod = "shutdownNow")
    public ExecutorService apiExecutionExecutor(ApiExecProperties properties) {
        int workers = Math.max(1, properties.getWorkers());
        AtomicInteger sequence = new AtomicInteger();
        ThreadFactory factory = runnable -> {
            Thread thread = new Thread(runnable, "api-exec-worker-" + sequence.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
        return new ThreadPoolExecutor(workers, workers, 0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(), factory, new ThreadPoolExecutor.AbortPolicy());
    }
}
