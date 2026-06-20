package com.fundtracker.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserProfileDTO {
    private String id;
    private String name;
    private String avatar;
    private String membership;
    private String membershipExpiry;
    private String phone;
    private String version;
}
