package com.kitepost.repository;

import com.kitepost.entity.UserAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<UserAccount, String> {
    Optional<UserAccount> findByEmail(String email);
    List<UserAccount> findAllByOrderByCreatedAtAsc();
    long countByRoleAndEnabledTrue(UserAccount.Role role);
}
