package com.vitc.repository;

import com.vitc.entity.PaymentGatewaySetting;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PaymentGatewaySettingRepository extends JpaRepository<PaymentGatewaySetting, Long> {

    Optional<PaymentGatewaySetting> findBySettingsKey(String settingsKey);
}
