package com.fundtracker.model.dto;

import lombok.Data;

@Data
public class SetPasswordReq {
    private String email;
    private String password;
    /** 密码注册时二次确认的验证码 */
    private String code;
}
