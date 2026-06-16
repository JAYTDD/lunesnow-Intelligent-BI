package com.lunesnow.manager;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import java.util.concurrent.TimeUnit;

/**
 * 图表任务限制器
 * 限制每个用户同时生成的图表数量，防止单用户抢占系统资源
 */
@Component
@Slf4j
public class ChartTaskLimiter {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * Redis key 前缀
     */
    private static final String KEY_PREFIX = "chart:task:limit:";

    /**
     * 每用户最大同时任务数
     */
    private static final int MAX_TASKS = 1;

    /**
     * key 过期时间（秒）- 10 分钟
     */
    private static final long EXPIRE_SECONDS = 600;

    /**
     * 尝试获取任务槽位
     *
     * @param userId 用户 ID
     * @return true=允许提交，false=超出限制
     */
    public boolean tryAcquire(Long userId) {
        String key = KEY_PREFIX + userId;

        try {
            // 读取当前任务数
            String countStr = stringRedisTemplate.opsForValue().get(key);
            int currentCount = countStr != null ? Integer.parseInt(countStr) : 0;

            // 判断是否超出限制
            if (currentCount >= MAX_TASKS) {
                log.warn("用户 {} 任务数量已满: {}/{}", userId, currentCount, MAX_TASKS);
                return false;
            }

            // 增加计数
            stringRedisTemplate.opsForValue().increment(key);
            // 设置过期时间（防止异常情况计数不归零）
            stringRedisTemplate.expire(key, EXPIRE_SECONDS, TimeUnit.SECONDS);

            log.info("用户 {} 获取任务槽位: {}/{}", userId, currentCount + 1, MAX_TASKS);
            return true;

        } catch (Exception e) {
            // Redis 异常时放行（降级处理）
            log.error("任务限制器异常，放行请求: userId={}", userId, e);
            return true;
        }
    }

    /**
     * 释放任务槽位
     *
     * @param userId 用户 ID
     */
    public void release(Long userId) {
        String key = KEY_PREFIX + userId;

        try {
            Long count = stringRedisTemplate.opsForValue().decrement(key);
            if (count != null && count <= 0) {
                stringRedisTemplate.delete(key);
                log.info("用户 {} 任务槽位已清空", userId);
            } else {
                log.info("用户 {} 释放任务槽位: 剩余 {}", userId, count);
            }
        } catch (Exception e) {
            log.error("释放任务槽位异常: userId={}", userId, e);
        }
    }

    /**
     * 查询用户当前任务数
     *
     * @param userId 用户 ID
     * @return 当前任务数
     */
    public int getCurrentCount(Long userId) {
        String key = KEY_PREFIX + userId;
        String countStr = stringRedisTemplate.opsForValue().get(key);
        return countStr != null ? Integer.parseInt(countStr) : 0;
    }

    /**
     * 查询用户是否已满
     *
     * @param userId 用户 ID
     * @return true=已满，false=未满
     */
    public boolean isFull(Long userId) {
        return getCurrentCount(userId) >= MAX_TASKS;
    }
}
