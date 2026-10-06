package com.vitc.repository;

import com.vitc.entity.PasswordResetOtp;
import com.vitc.entity.enums.RecoveryPortal;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PasswordResetOtpRepository extends JpaRepository<PasswordResetOtp, Long> {

    Optional<PasswordResetOtp> findByRecoveryToken(String recoveryToken);

    Optional<PasswordResetOtp> findByResetToken(String resetToken);

    Optional<PasswordResetOtp> findByRecoveryTokenAndAccountRole(String recoveryToken, RecoveryPortal accountRole);

    Optional<PasswordResetOtp> findByResetTokenAndAccountRole(String resetToken, RecoveryPortal accountRole);

    List<PasswordResetOtp> findByAccountIdAndAccountRoleAndUsedFalse(Long accountId, RecoveryPortal accountRole);
}

