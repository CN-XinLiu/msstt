package com.lx.msstt.constant;

/**
 * Redis 常量
 */
public interface RedisConstant {
    /**
     * 用户签到记录的key Redis 前缀
     */
    String USER_SIGN_IN_REDIS_KEY_PREFIX = "user:signins";

    /**
     * 获取用户签到记录的Redis key
     * @param year 年份
     * @param userId 用户id
     * @return Redis key
     */
    static String getUserSignInRedisKey(int year, long userId) {
        return String.format("%s:%s:%s", USER_SIGN_IN_REDIS_KEY_PREFIX, year, userId);
    }
}
