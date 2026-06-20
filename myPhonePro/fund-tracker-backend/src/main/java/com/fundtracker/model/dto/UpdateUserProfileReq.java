package com.fundtracker.model.dto;

import lombok.Data;

@Data
public class UpdateUserProfileReq {
    private String name;
    private String avatar;
    private String phone;
}
