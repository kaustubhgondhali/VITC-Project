package com.vitc.repository;

import com.vitc.entity.SmtpSetting;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SmtpSettingRepository extends JpaRepository<SmtpSetting, Long> {

    Optional<SmtpSetting> findBySettingsKey(String settingsKey);
}
