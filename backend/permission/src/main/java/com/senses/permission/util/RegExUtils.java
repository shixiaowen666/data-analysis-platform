package com.senses.permission.util;

import org.apache.commons.lang3.StringUtils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 正则表达式工具类
 */
public class RegExUtils {
    /**
     * 校验字符串是否由字母和数字组成
     */
    public static boolean isAlphaNumeric(String s){
        Pattern p = Pattern.compile("[0-9a-zA-Z]{1,}");
        Matcher m = p.matcher(s);
        return m.matches();
    }

    /**
     * 校验密码，包含大写字母，小写字母，数字，特殊符号等任意三项，长度在6-20位
     */
    public static boolean checkPassword(String s){
        String regex = "^(?![a-zA-Z]+$)(?![A-Z0-9]+$)(?![A-Z\\W]+$)(?![a-z0-9]+$)(?![a-z\\W]+$)(?![0-9\\W]+$)[a-zA-Z0-9\\W]{6,20}$";
        return Pattern.matches(regex, s);
    }


}
