package com.sms.backend.repository;

import com.sms.backend.entity.User;
import com.sms.backend.enums.AccountStatus;
import com.sms.backend.enums.RoleName;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);
    Optional<User> findByEmail(String email);
    boolean existsByUsername(String username);
    boolean existsByEmail(String email);
    boolean existsByRole_RoleName(RoleName roleName);
    List<User> findByStatus(AccountStatus status);

    // ---- UC04 / UC05 / UC06 ----

    boolean existsByUsernameAndUserIdNot(String username, Long userId);

    boolean existsByEmailAndUserIdNot(String email, Long userId);

    Optional<User> findByEmployee_EmployeeId(Long employeeId);

    Optional<User> findBySupplier_SupplierId(Long supplierId);

    long countByRole_RoleId(Long roleId);

    @Query("SELECT u.employee.employeeId FROM User u WHERE u.employee IS NOT NULL")
    List<Long> findLinkedEmployeeIds();

    @Query("SELECT u.supplier.supplierId FROM User u WHERE u.supplier IS NOT NULL")
    List<Long> findLinkedSupplierIds();

    /**
     * UC04 - View Users: search by username/email, filter by role and status.
     * Deleted accounts (email ending with @deleted.invalid, see UserServiceImpl#deleteUser) are hidden.
     */
    @Query("""
            SELECT u FROM User u
            WHERE u.email NOT LIKE '%@deleted.invalid'
              AND (:search IS NULL OR LOWER(u.username) LIKE :search OR LOWER(u.email) LIKE :search)
              AND (:roleId IS NULL OR u.role.roleId = :roleId)
              AND (:status IS NULL OR u.status = :status)
            """)
    Page<User> search(@Param("search") String search,
                      @Param("roleId") Long roleId,
                      @Param("status") AccountStatus status,
                      Pageable pageable);
}
