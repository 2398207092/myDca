package com.fundtracker.model.dto;

import lombok.Data;

@Data
public class LoginReq {
    private String email;
    /** 验证码登录时传 code，密码登录时传 password */
    private String code;
    private String password;
}
