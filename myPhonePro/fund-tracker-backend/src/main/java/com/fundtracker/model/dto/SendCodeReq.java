package com.fundtracker.model.dto;

import lombok.Data;

@Data
public class SendCodeReq {
    private String email;
    /** login / register / set_password */
    private String type;
}
