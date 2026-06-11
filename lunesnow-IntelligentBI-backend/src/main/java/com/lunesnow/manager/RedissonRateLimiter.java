package com.lunesnow.manager;

import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RRateLimiter;
import org.redisson.api.RateIntervalUnit;
import org.redisson.api.RateType;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import java.util.HashMap;
import java.util.Map;

/**
 * 基于 Redisson 的分布式限流管理器
 * 使用 RRateLimiter 实现令牌桶限流
 *
 * @author lunesnow
 */
@Component
@Slf4j
public class RedissonRateLimiter {

    @Resource
    private RedissonClient redissonClient;

    /**
     * 限流 key 前缀
     */
    private static final String RATE_LIMIT_PREFIX = "rate_limit:";

    /**
     * 尝试获取令牌
     *
     * @param key              限流 key
     * @param permitsPerSecond 每秒允许的请求数（令牌生成速率）
     * @param burstCapacity    桶容量（最大令牌数）
     * @return true 表示允许，false 表示拒绝
     */
    public boolean tryAcquire(String key, double permitsPerSecond, double burstCapacity) {
        try {
            RRateLimiter rateLimiter = redissonClient.getRateLimiter(key);

            // 尝试设置限流器参数（只在第一次设置，后续调用会忽略）
            rateLimiter.trySetRate(
                    RateType.OVERALL,  // 全局限流（分布式）
                    (long) permitsPerSecond,  // 每秒令牌数
                    (long) burstCapacity,     // 桶容量
                    RateIntervalUnit.SECONDS
            );

            // 尝试获取令牌
            boolean acquired = rateLimiter.tryAcquire();

            if (!acquired) {
                log.warn("请求被限流: key={}, permitsPerSecond={}, burstCapacity={}",
                        key, permitsPerSecond, burstCapacity);
            }

            return acquired;
        } catch (Exception e) {
            log.error("限流处理异常: key={}", key, e);
            // 限流异常时放行请求（降级处理）
            return true;
        }
    }

    /**
     * 获取限流器状态
     *
     * @param key 限流 key
     * @return 限流器状态信息
     */
    public Map<String, Object> getStatus(String key) {
        Map<String, Object> status = new HashMap<>();
        try {
            RRateLimiter rateLimiter = redissonClient.getRateLimiter(key);
            status.put("key", key);
            status.put("availableTokens", rateLimiter.availablePermits());
            status.put("exists", true);
        } catch (Exception e) {
            log.error("获取限流状态失败: key={}", key, e);
            status.put("key", key);
            status.put("exists", false);
            status.put("error", e.getMessage());
        }
        return status;
    }

    /**
     * 重置限流器
     *
     * @param key 限流 key
     */
    public void reset(String key) {
        try {
            RRateLimiter rateLimiter = redissonClient.getRateLimiter(key);
            rateLimiter.delete();
            log.info("限流器已重置: key={}", key);
        } catch (Exception e) {
            log.error("重置限流器失败: key={}", key, e);
        }
    }

    /**
     * 构建完整的限流 key
     *
     * @param prefix key 前缀（如 IP、用户 ID 等）
     * @return 完整的 key
     */
    public String buildKey(String prefix) {
        return RATE_LIMIT_PREFIX + prefix;
    }
}
