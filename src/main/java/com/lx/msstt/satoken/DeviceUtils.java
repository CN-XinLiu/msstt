package com.lx.msstt.satoken;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.Header;
import cn.hutool.http.useragent.UserAgent;
import cn.hutool.http.useragent.UserAgentUtil;
import com.lx.msstt.common.ErrorCode;
import com.lx.msstt.exception.ThrowUtils;

import javax.servlet.http.HttpServletRequest;

public class DeviceUtils {
    /**
     * 根据请求获取设备信息
     */
    public static String getRequestDevice(HttpServletRequest request) {
        String userAgentStr = request.getHeader(Header.USER_AGENT.toString());
        // 使用HuTool解析UserAgent
        UserAgent userAgent = UserAgentUtil.parse(userAgentStr);
        ThrowUtils.throwIf(userAgent == null, ErrorCode.OPERATION_ERROR, "非法请求");
        // 默认值为pc段
        String device = "pc";
        // 是否为小程序
        if (isMiniProgram(userAgentStr)) {
            device = "miniProgram";
        } else if (isPad(userAgentStr)) {
            // 是否为pad
            device = "pad";
        } else if (userAgent.isMobile()) {
            // 是否为手机
            device = "mobile";
        }
        return device;
    }

    /**
     * 判断是否为小程序
     * 一般通过User-Agent字符串中的”MicroMessenger“来判断是否为小程序
     */
    private static boolean isMiniProgram(String userAgentStr) {
        return StrUtil.containsIgnoreCase(userAgentStr, "MicroMessenger")
                && StrUtil.containsIgnoreCase(userAgentStr, "MiniProgram");
    }

    /**
     * 判断是否为Pad
     */
    private static boolean isPad(String userAgentStr) {
        // 检查iPad的User-Agent标志
        boolean isIpad = StrUtil.containsIgnoreCase(userAgentStr, "iPad");
        boolean isAndroidTablet = StrUtil.containsIgnoreCase(userAgentStr, "Android")
                && !StrUtil.containsIgnoreCase(userAgentStr, "Mobile");

        return isIpad || isAndroidTablet;
    }
}
