package com.sms.backend.security;

import com.sms.backend.enums.AccountStatus;
import com.sms.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Loads the user of a token on every request, so that:
 * - a deactivated / locked / deleted account is rejected immediately,
 * - permission changes made in UC06 apply from the next request (SRS UC06 POS-02),
 * - a renamed account (UC05) keeps working (the token is matched by user id, not username).
 */
@Component
@RequiredArgsConstructor
public class AuthUserLoader {

    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public Optional<UserDetails> loadActiveUser(Long userId) {
        return userRepository.findById(userId)
                .filter(user -> user.getStatus() == AccountStatus.ACTIVE)
                .map(SecurityUserMapper::toUserDetails);
    }
}
