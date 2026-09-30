package com.common.base;

import lombok.Data;

@Data
public class LoginUser {
    private  String username;
    private  String password;
    private  String token;
    private  Long tenantId;
    private Long userId;

    @Override
    public String toString() {
        return "LoginUser{" +
                "username='" + username + '\'' +
                ", password='" + password + '\'' +
                ", token='" + token + '\'' +
                ", userId='" + userId + '\'' +
                '}';
    }
}
