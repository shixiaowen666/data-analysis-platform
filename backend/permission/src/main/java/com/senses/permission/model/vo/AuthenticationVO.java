package com.senses.permission.model.vo;

import com.senses.permission.entity.User;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.io.Serializable;

@Getter
@AllArgsConstructor
public class AuthenticationVO implements Serializable {

    private final String token;

    private final User user;
}
