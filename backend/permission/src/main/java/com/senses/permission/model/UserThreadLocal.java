package com.senses.permission.model;

/**
 * author: zjd
 * date: 20191028
 */
public class UserThreadLocal {

    //把构造函数私有
    private UserThreadLocal(){

    }
    //线程副本
    private static final ThreadLocal<LoginUser> LOCAL = new ThreadLocal<LoginUser>();

    public static void set(LoginUser user){
        LOCAL.set(user);
    }

    public static LoginUser get(){
        return LOCAL.get();
    }

    public static void remove(){
        LOCAL.remove();
    }
}