package com.lx.msstt.constant;

/**
 * Hotkey 常量
 */
public interface HotkeyConstant {
    /**
     * 获取题库详情的 key 前缀
     */
    String USER_BANK_DETAIL_KEY_PREFIX = "bank_detail_";

    /**
     * 获取用户获取题库详情 key
     * @param id 题库id
     * @return hot key
     */
    static String getUserBankDetailKeyPrefix(long id) {
        return String.format("%s%s", USER_BANK_DETAIL_KEY_PREFIX, id);
    }
}
