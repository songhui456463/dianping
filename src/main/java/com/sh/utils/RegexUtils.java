package com.sh.utils;

import cn.hutool.core.util.StrUtil;

public class RegexUtils {

    /**
     * 是否为无效手机格式
     * @param phone 手机号
     * @return true:符合，false：不符合
     */
    public static boolean isPhoneInvalid(String phone) {
        return mismatch(phone, RegexPatterns.PHONE_REGEX);
    }

    /**
     * 是否为无效邮箱格式
     * @param email 邮箱
     * @return true:符合，false：不符合
     */
    public static boolean isEmailInvalid(String email) {
        return mismatch(email, RegexPatterns.EMAIL_REGEX);
    }

    /**
     * 是否为无效验证码
     * @param code 验证码
     * @return true:符合，false：不符合
     */
    public static boolean isCodeInvalid(String code) {
        return mismatch(code, RegexPatterns.VERIFY_CODE_REGEX);
    }

    // 校验是否不符合正则格式
    private static  boolean mismatch(String str, String regex) {
        // 1 判断是否为空
        if (StrUtil.isBlank(str)) {
            return true;
        }

        // 2 判断是否字符串是否不符合正则表达式
        return !str.matches(regex);
    }
}
