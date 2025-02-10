package com.lx.msstt.manager;

import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RScript;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.IntegerCodec;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.Instant;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class CounterManager {
    @Resource
    private RedissonClient redissonClient;

    /**
     * 增加并返回技术
     * @param key 键
     * @return
     */
    public long incrAndGetCounter(String key) {
        return incrAndGetCounter(key, 1, TimeUnit.MINUTES);
    }

    /**
     * 增加并返回技术
     * @param key 键
     * @param timeInterval 时间间隔
     * @param timeUnit 时间间隔单位
     * @return
     */
    public long incrAndGetCounter(String key, int timeInterval, TimeUnit timeUnit) {
        int expirationTimeInSeconds;
        switch (timeUnit) {
            case SECONDS:
                expirationTimeInSeconds = timeInterval;
                break;
            case MINUTES:
                expirationTimeInSeconds = timeInterval * 60;
                break;
            case HOURS:
                expirationTimeInSeconds = timeInterval * 3600;
                break;
            default:
                throw new IllegalArgumentException("Unsupported TimeUnit. Use SECONDS, MINUTES, or HOURS.");
        }

        return incrAndGetCounter(key, timeInterval, timeUnit, expirationTimeInSeconds);
    }

    /**
     * 增加并返回技术
     * @param key 键
     * @param timeInterval 时间间隔
     * @param timeUnit 时间间隔单位
     * @param expirationTimeInSeconds 计数器缓存过期时间
     * @return
     */
    public long incrAndGetCounter(String key, int timeInterval, TimeUnit timeUnit,
                                  long expirationTimeInSeconds) {
        if (StrUtil.isBlank(key)) {
            return 0;
        }

        // 根据时间粒度生成 Redis key
        long timeFactor;
        switch (timeUnit) {
            case SECONDS:
                timeFactor = Instant.now().getEpochSecond() / timeInterval;
                break;
            case MINUTES:
                timeFactor = Instant.now().getEpochSecond() / timeInterval / 60;
                break;
            case HOURS:
                timeFactor = Instant.now().getEpochSecond() / timeInterval / 3600;
                break;
            default:
                throw new IllegalArgumentException("不支持的单位");
        }

        String redisKey = key + ":" + timeFactor;

        // Lua脚本
        String luaScript =
                "if redis.call('exists', KEYS[1]) == 1 then " +
                        "  return redis.call('incr', KEYS[1]); " +
                        "else " +
                        "  redis.call('set', KEYS[1], 1); " +
                        "  redis.call('expire', KEYS[1], 180); " +  // 设置 180 秒过期时间
                        "  return 1; " +
                        "end";
        // 执行lua脚本
        RScript script = redissonClient.getScript(IntegerCodec.INSTANCE);
        Object countObj = script.eval(
                RScript.Mode.READ_WRITE,
                luaScript, // lua脚本定义
                RScript.ReturnType.INTEGER, // 返回值类型
                Collections.singletonList(redisKey), // 第一个参数
                expirationTimeInSeconds // 第二个参数
        );

        return (long) countObj;
    }
}
