package com.lunesnow.aop;

import com.lunesnow.annotation.RateLimit;
import com.lunesnow.common.ErrorCode;
import com.lunesnow.exception.BusinessException;
import com.lunesnow.manager.RedissonRateLimiter;
import com.lunesnow.model.entity.User;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 限流切面
 * 拦截 @RateLimit 注解的方法，进行分布式限流
 *
 * @author lunesnow
 */
@Aspect
@Component
@Slf4j
public class RateLimitAspect {

    @Resource
    private RedissonRateLimiter redissonRateLimiter;

    /**
     * 限流 key 前缀
     */
    private static final String RATE_LIMIT_PREFIX = "rate_limit:";

    /**
     * 请求计数器（用于统计被限流的请求）
     */
    private static final AtomicLong LIMIT_COUNT = new AtomicLong(0);

    @Before("@annotation(com.lunesnow.annotation.RateLimit)")
    public void doBefore(JoinPoint joinPoint) {
        // 获取方法和注解
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        RateLimit rateLimit = method.getAnnotation(RateLimit.class);

        if (rateLimit == null) {
            return;
        }

        // 构建限流 key
        String key = buildKey(rateLimit, joinPoint);

        // 使用 Redisson 限流器
        boolean allowed = redissonRateLimiter.tryAcquire(
                key,
                rateLimit.permitsPerSecond(),
                rateLimit.burstCapacity()
        );

        if (!allowed) {
            // 记录限流次数
            long count = LIMIT_COUNT.incrementAndGet();
            log.warn("请求被限流: key={}, count={}, method={}", key, count, method.getName());

            throw new BusinessException(ErrorCode.OPERATION_ERROR, rateLimit.message());
        }

        log.debug("请求通过限流: key={}, method={}", key, method.getName());
    }

    /**
     * 构建限流 key
     */
    private String buildKey(RateLimit rateLimit, JoinPoint joinPoint) {
        StringBuilder keyBuilder = new StringBuilder(RATE_LIMIT_PREFIX);

        // 根据限流类型构建 key
        switch (rateLimit.limitType()) {
            case IP:
                keyBuilder.append("ip:").append(getClientIp());
                break;
            case USER:
                keyBuilder.append("user:").append(getUserId());
                break;
            default:
                // 使用方法全限定名
                keyBuilder.append(joinPoint.getSignature().getDeclaringTypeName())
                        .append(":")
                        .append(joinPoint.getSignature().getName());
                break;
        }

        // 如果指定了自定义 key，追加到后面
        if (!rateLimit.key().isEmpty()) {
            keyBuilder.append(":").append(rateLimit.key());
        }

        return keyBuilder.toString();
    }

    /**
     * 获取客户端 IP
     */
    private String getClientIp() {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes)
                    RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                String ip = request.getHeader("X-Forwarded-For");
                if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
                    ip = request.getHeader("Proxy-Client-IP");
                }
                if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
                    ip = request.getHeader("WL-Proxy-Client-IP");
                }
                if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
                    ip = request.getRemoteAddr();
                }
                // 多次反向代理后会有多个IP，取第一个
                if (ip != null && ip.contains(",")) {
                    ip = ip.split(",")[0].trim();
                }
                return ip;
            }
        } catch (Exception e) {
            log.error("获取客户端 IP 失败", e);
        }
        return "unknown";
    }

    /**
     * 获取用户 ID
     * 从 Session 中获取登录用户信息（key: "user_login"）
     */
    private String getUserId() {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes)
                    RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                // 从 session 中获取登录用户（key 为 "user_login"，值为 User 对象）
                Object userObj = request.getSession().getAttribute("user_login");
                if (userObj instanceof User user) {
                    return user.getId().toString();
                }
            }
        } catch (Exception e) {
            log.error("获取用户 ID 失败", e);
        }
        return "anonymous";
    }
}
