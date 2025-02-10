package com.lx.msstt.aop;

import cn.dev33.satoken.stp.StpUtil;
import com.alibaba.nacos.api.config.annotation.NacosValue;
import com.lx.msstt.annotation.CrawlerCheck;
import com.lx.msstt.common.ErrorCode;
import com.lx.msstt.exception.BusinessException;
import com.lx.msstt.manager.CounterManager;
import com.lx.msstt.model.entity.User;
import com.lx.msstt.service.UserService;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.concurrent.TimeUnit;

/**
 * 爬虫校验AOP
 */
@Aspect
@Component
public class CrawlerCheckInterceptor {
    @Resource
    UserService userService;

    @Resource
    CounterManager counterManager;

    // 调用多少次时告警
    @NacosValue(value = "${warn.count: 10}", autoRefreshed = true)
    private Integer warn_count;

    // 调用多少次时封号
    @NacosValue(value = "${ban.count: 20}", autoRefreshed = true)
    private Integer ban_count;

    @Around("@annotation(crawlerCheck)")
    public Object doInterceptor(ProceedingJoinPoint joinPoint, CrawlerCheck crawlerCheck) throws Throwable {
        RequestAttributes requestAttributes = RequestContextHolder.currentRequestAttributes();
        HttpServletRequest request = ((ServletRequestAttributes) requestAttributes).getRequest();
        User loginUser = userService.getLoginUser(request);
        Long longUserId = loginUser.getId();

        int timeInterval = crawlerCheck.timeInterval();
        TimeUnit timeUnit = crawlerCheck.timeUnit();
        long expirationTimeInSeconds = crawlerCheck.expirationTimeInSeconds();
        // 拼接访问 key
        String key = String.format("user:access:%s", longUserId);
        // 统计一分钟内访问次数, 180秒过期
        long count = counterManager.incrAndGetCounter(
                key, timeInterval, timeUnit, expirationTimeInSeconds
        );
        // 是否封号
        if (count > ban_count) {
            // 踢下线
            StpUtil.kickout(longUserId);
            // 封号
            User updateUser = new User();
            updateUser.setId(longUserId);
            updateUser.setUserRole("ban");
            userService.updateById(updateUser);
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "访问次数过多，已被封号");
        } else if (count == warn_count) {
            throw new BusinessException(110, "警告：访问太频繁");
        }

        // 通过爬虫校验，放行
        return joinPoint.proceed();
    }
}
