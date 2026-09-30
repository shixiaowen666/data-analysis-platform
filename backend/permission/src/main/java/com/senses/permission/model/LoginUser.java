package com.senses.permission.model;

import lombok.Data;

@Data
public class LoginUser {
    private  String username;
    private  String password;
    private  String token;
    private  Long tenantId;


//    @Override
//    public String toString() {
//        return "LoginUser{" +
//                "username='" + username + '\'' +
//                ", password='" + password + '\'' +
//                ", token='" + token + '\'' +
//                '}';
//    }
}
