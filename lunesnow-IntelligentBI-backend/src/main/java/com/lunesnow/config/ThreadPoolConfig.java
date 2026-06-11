package com.lunesnow.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 线程池配置 - 用于图表异步生成任务
 */
@Configuration
public class ThreadPoolConfig {

    /**
     * 图表任务执行线程池
     * 核心线程数: 4
     * 最大线程数: 4
     * 队列容量: 100
     * 拒绝策略: AbortPolicy (队列满时直接拒绝)
     */
    @Bean("chartTaskExecutor")
    public ThreadPoolExecutor chartTaskExecutor() {
        return new ThreadPoolExecutor(
                4,    // 核心线程数为4
                4,    // 最大线程数为4
                60L,    // 60秒
                TimeUnit.SECONDS,    // 时间单位为秒
                new ArrayBlockingQueue<>(100),    // 队列容量为100
                new ThreadFactory() {
                    private final AtomicInteger counter = new AtomicInteger(0);

                    @Override
                    public Thread newThread(Runnable r) {
                        Thread t = new Thread(r, "chart-task-" + counter.incrementAndGet());
                        t.setDaemon(true);
                        return t;
                    }
                }, // 线程工厂
                new ThreadPoolExecutor.AbortPolicy()    // 直接拒绝任务
        );
    }
}
