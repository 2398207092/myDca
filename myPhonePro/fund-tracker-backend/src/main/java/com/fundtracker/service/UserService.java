package com.fundtracker.service;

import com.fundtracker.model.dto.*;
import com.fundtracker.model.entity.UserProfile;
import com.fundtracker.model.entity.UserSettings;
import com.fundtracker.model.enums.ForecastHorizon;
import com.fundtracker.repository.UserProfileRepository;
import com.fundtracker.repository.UserSettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserProfileRepository userProfileRepository;
    private final UserSettingsRepository userSettingsRepository;

    public UserProfileDTO getProfile() {
        UserProfile profile = getOrCreateProfile();
        return UserProfileDTO.builder()
                .id(profile.getId())
                .name(profile.getName())
                .avatar(profile.getAvatar())
                .membership(profile.getMembership().name())
                .membershipExpiry(profile.getMembershipExpiry() != null ?
                        profile.getMembershipExpiry().toString() : null)
                .phone(profile.getPhone())
                .version("2.4.0")
                .build();
    }

    @Transactional
    public UserProfileDTO updateProfile(UpdateUserProfileReq req) {
        UserProfile profile = getOrCreateProfile();
        if (req.getName() != null) profile.setName(req.getName());
        if (req.getAvatar() != null) profile.setAvatar(req.getAvatar());
        if (req.getPhone() != null) profile.setPhone(req.getPhone());
        profile = userProfileRepository.save(profile);

        return UserProfileDTO.builder()
                .id(profile.getId())
                .name(profile.getName())
                .avatar(profile.getAvatar())
                .membership(profile.getMembership().name())
                .membershipExpiry(profile.getMembershipExpiry() != null ?
                        profile.getMembershipExpiry().toString() : null)
                .phone(profile.getPhone())
                .version("2.4.0")
                .build();
    }

    public UserSettingsDTO getSettings() {
        UserSettings settings = getOrCreateSettings();
        return UserSettingsDTO.builder()
                .currency(settings.getCurrency())
                .currencyLabel(settings.getCurrencyLabel())
                .forecastHorizon(settings.getForecastHorizon().name())
                .customForecastValue(settings.getCustomForecastValue())
                .notificationsEnabled(settings.isNotificationsEnabled())
                .build();
    }

    @Transactional
    public UserSettingsDTO updateSettings(UpdateUserSettingsReq req) {
        UserSettings settings = getOrCreateSettings();
        if (req.getCurrency() != null) settings.setCurrency(req.getCurrency());
        if (req.getCurrencyLabel() != null) settings.setCurrencyLabel(req.getCurrencyLabel());
        if (req.getForecastHorizon() != null) {
            settings.setForecastHorizon(ForecastHorizon.valueOf(req.getForecastHorizon()));
        }
        if (req.getCustomForecastValue() != null) {
            settings.setCustomForecastValue(req.getCustomForecastValue());
        }
        if (req.getNotificationsEnabled() != null) {
            settings.setNotificationsEnabled(req.getNotificationsEnabled());
        }
        settings = userSettingsRepository.save(settings);

        return UserSettingsDTO.builder()
                .currency(settings.getCurrency())
                .currencyLabel(settings.getCurrencyLabel())
                .forecastHorizon(settings.getForecastHorizon().name())
                .customForecastValue(settings.getCustomForecastValue())
                .notificationsEnabled(settings.isNotificationsEnabled())
                .build();
    }

    private UserProfile getOrCreateProfile() {
        List<UserProfile> all = userProfileRepository.findAll();
        if (all.isEmpty()) {
            UserProfile profile = UserProfile.builder()
                    .id(UUID.randomUUID().toString())
                    .name("稳健投资者")
                    .avatar("https://api.dicebear.com/7.x/thumbs/svg?seed=fund-tracker&backgroundColor=ff7a45")
                    .membership(com.fundtracker.model.enums.MembershipType.pro)
                    .membershipExpiry(java.time.LocalDate.of(2025, 12, 31))
                    .phone("138****8888")
                    .build();
            return userProfileRepository.save(profile);
        }
        return all.get(0);
    }

    private UserSettings getOrCreateSettings() {
        List<UserSettings> all = userSettingsRepository.findAll();
        if (all.isEmpty()) {
            UserSettings settings = UserSettings.builder()
                    .id(UUID.randomUUID().toString())
                    .currency("CNY")
                    .currencyLabel("人民币")
                    .forecastHorizon(ForecastHorizon._3y)
                    .notificationsEnabled(true)
                    .build();
            return userSettingsRepository.save(settings);
        }
        return all.get(0);
    }
}
