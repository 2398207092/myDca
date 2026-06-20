package com.fundtracker.repository;

import com.fundtracker.model.entity.UserSettings;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserSettingsRepository extends JpaRepository<UserSettings, String> {
}
