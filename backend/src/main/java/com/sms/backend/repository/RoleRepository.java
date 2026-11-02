package com.sms.backend.repository;

import com.sms.backend.entity.Role;
import com.sms.backend.enums.GeneralStatus;
import com.sms.backend.enums.RoleName;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RoleRepository extends JpaRepository<Role, Long> {

    Optional<Role> findByRoleName(RoleName roleName);

    List<Role> findByStatus(GeneralStatus status);
}
