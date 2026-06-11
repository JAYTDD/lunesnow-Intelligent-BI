package com.lunesnow.controller;

import com.lunesnow.annotation.AuthCheck;
import com.lunesnow.common.BaseResponse;
import com.lunesnow.common.ResultUtils;
import com.lunesnow.constant.UserConstant;
import com.lunesnow.manager.RedissonRateLimiter;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 限流管理接口
 *
 * @author lunesnow
 */
@RestController
@RequestMapping("/rate-limit")
@Slf4j
public class RateLimitController {

    @Resource
    private RedissonRateLimiter redissonRateLimiter;

    /**
     * 获取限流状态（管理员）
     */
    @GetMapping("/status")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Map<String, Object>> getRateLimitStatus(@RequestParam String key) {
        Map<String, Object> status = redissonRateLimiter.getStatus(key);
        return ResultUtils.success(status);
    }

    /**
     * 重置限流（管理员）
     */
    @PostMapping("/reset")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> resetRateLimit(@RequestParam String key) {
        redissonRateLimiter.reset(key);
        log.info("管理员重置限流: key={}", key);
        return ResultUtils.success(true);
    }
}
