package com.lunesnow.manager;

import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RRateLimiter;
import org.redisson.api.RateIntervalUnit;
import org.redisson.api.RateType;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
        status.put("key", key);
        try {
            RRateLimiter rateLimiter = redissonClient.getRateLimiter(key);
            // availablePermits 会触发 Lua 脚本，如果限流器未初始化会抛异常
            long tokens = rateLimiter.availablePermits();
            status.put("exists", true);
            status.put("availableTokens", tokens);
        } catch (Exception e) {
            // 限流器未初始化或 key 不存在，都视为不存在
            status.put("exists", false);
            status.put("availableTokens", 0);
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
     * 获取所有限流状态
     *
     * @return 所有限流器的状态列表
     */
    public List<Map<String, Object>> listAll() {
        List<Map<String, Object>> list = new ArrayList<>();
        try {
            // 扫描所有 rate_limit: 开头的 key
            Iterable<String> keysIterable = redissonClient.getKeys().getKeysByPattern(RATE_LIMIT_PREFIX + "*");
            Set<String> keys = new HashSet<>();
            keysIterable.forEach(keys::add);
            log.info("扫描到 {} 个限流相关 key", keys.size());
            for (String key : keys) {
                // 只处理主 key（不包含 :value 和 :permits 的）
                if (key.contains(":value") || key.contains(":permits")) {
                    continue;
                }
                try {
                    RRateLimiter rateLimiter = redissonClient.getRateLimiter(key);
                    long tokens = rateLimiter.availablePermits();
                    Map<String, Object> item = new HashMap<>();
                    item.put("key", key);
                    // 提取类型和标识
                    String displayKey = key.replace(RATE_LIMIT_PREFIX, "");
                    item.put("type", displayKey.split(":")[0]);
                    item.put("identifier", displayKey.split(":").length > 1 ? displayKey.split(":")[1] : "");
                    item.put("availableTokens", tokens);
                    item.put("exists", true);
                    list.add(item);
                } catch (Exception e) {
                    log.warn("读取限流 key 失败: {}", key, e);
                }
            }
        } catch (Exception e) {
            log.error("获取所有限流状态失败", e);
        }
        return list;
    }

    /**
     * 重置所有限流器
     */
    public void resetAll() {
        try {
            Iterable<String> keysIterable = redissonClient.getKeys().getKeysByPattern(RATE_LIMIT_PREFIX + "*");
            Set<String> keys = new HashSet<>();
            keysIterable.forEach(keys::add);
            int count = 0;
            for (String key : keys) {
                try {
                    RRateLimiter rateLimiter = redissonClient.getRateLimiter(key);
                    rateLimiter.delete();
                    count++;
                } catch (Exception e) {
                    // 跳过无法删除的 key
                }
            }
            log.info("批量重置限流器完成，共删除 {} 个", count);
        } catch (Exception e) {
            log.error("批量重置限流器失败", e);
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
