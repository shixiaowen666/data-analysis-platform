package com.chatbi.chat.models;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;

@Data
public class SaasUser implements Serializable {

    private static final long serialVersionUID = -7586775777492558983L;
    private Long id;
    private String token;
    private String account;
    private String username;
    private String name;
    private String mobile;
    private String email;
    private Integer deptId;
    private String deptFullId;
    private String deptFullName;
    private Long tenantId;
    private Date lastPasswordResetTime;

}
