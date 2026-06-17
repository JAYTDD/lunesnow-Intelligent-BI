package com.lunesnow.manager;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import java.util.Collections;

/**
 * 图表任务限制器
 * 限制每个用户同时生成的图表数量，防止单用户抢占系统资源
 *
 * 使用 Lua 脚本保证 check-and-increment / decrement 操作的原子性，
 * 消除原来 get + check + increment 的竞态条件。
 */
@Component
@Slf4j
public class ChartTaskLimiter {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * Lua 脚本：原子性地尝试获取任务槽位
     * KEYS[1] = 计数 key
     * ARGV[1] = MAX_TASKS
     * ARGV[2] = EXPIRE_SECONDS
     */
    private static final String ACQUIRE_LUA_SCRIPT =
            "local current = tonumber(redis.call('GET', KEYS[1]) or '0') " +
            "if current < tonumber(ARGV[1]) then " +
            "  local newVal = redis.call('INCR', KEYS[1]) " +
            "  redis.call('EXPIRE', KEYS[1], tonumber(ARGV[2])) " +
            "  return newVal " +
            "else " +
            "  return 0 " +
            "end";

    /**
     * Lua 脚本：原子性地释放任务槽位（安全递减，不会低于 0）
     * KEYS[1] = 计数 key
     */
    private static final String RELEASE_LUA_SCRIPT =
            "local current = tonumber(redis.call('GET', KEYS[1]) or '0') " +
            "if current <= 0 then " +
            "  return 0 " +
            "end " +
            "local newVal = redis.call('DECR', KEYS[1]) " +
            "if newVal <= 0 then " +
            "  redis.call('DEL', KEYS[1]) " +
            "  return 0 " +
            "end " +
            "return newVal";

    private DefaultRedisScript<Long> acquireScript;
    private DefaultRedisScript<Long> releaseScript;

    @PostConstruct
    public void init() {
        acquireScript = new DefaultRedisScript<>(ACQUIRE_LUA_SCRIPT, Long.class);
        releaseScript = new DefaultRedisScript<>(RELEASE_LUA_SCRIPT, Long.class);
    }

    /**
     * Redis key 前缀
     */
    private static final String KEY_PREFIX = "chart:task:limit:";

    /**
     * 每用户最大同时任务数
     */
    private static final int MAX_TASKS = 3;

    /**
     * key 过期时间（秒）- 10 分钟
     */
    private static final long EXPIRE_SECONDS = 600;

    /**
     * 尝试获取任务槽位（原子操作，无竞态条件）
     *
     * @param userId 用户 ID
     * @return true=允许提交，false=超出限制
     */
    public boolean tryAcquire(Long userId) {
        String key = KEY_PREFIX + userId;

        try {
            Long result = stringRedisTemplate.execute(
                    acquireScript,
                    Collections.singletonList(key),
                    String.valueOf(MAX_TASKS),
                    String.valueOf(EXPIRE_SECONDS)
            );

            if (result != null && result > 0) {
                log.info("用户 {} 获取任务槽位: {}/{}", userId, result, MAX_TASKS);
                return true;
            }

            log.warn("用户 {} 任务数量已满: {}/{}", userId, MAX_TASKS, MAX_TASKS);
            return false;

        } catch (Exception e) {
            // Redis 异常时放行（降级处理）
            log.error("任务限制器异常，放行请求: userId={}", userId, e);
            return true;
        }
    }

    /**
     * 释放任务槽位（原子操作，保证不会降到负数）
     *
     * @param userId 用户 ID
     */
    public void release(Long userId) {
        String key = KEY_PREFIX + userId;

        try {
            Long count = stringRedisTemplate.execute(
                    releaseScript,
                    Collections.singletonList(key)
            );

            if (count != null && count == 0) {
                log.info("用户 {} 任务槽位已清空", userId);
            } else if (count != null) {
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
