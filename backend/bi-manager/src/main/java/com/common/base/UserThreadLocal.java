package com.common.base;

import com.common.models.SaasUser;

/**
 * author: zjd
 * date: 20191028
 */
public class UserThreadLocal {

    //把构造函数私有
    private UserThreadLocal(){

    }
    //线程副本
    private static final ThreadLocal<SaasUser> LOCAL = new ThreadLocal<SaasUser>();

    public static void set(SaasUser user){
        LOCAL.set(user);
    }

    public static SaasUser get(){
        return LOCAL.get();
    }
    public static void remove(){
        LOCAL.remove();
    }
}