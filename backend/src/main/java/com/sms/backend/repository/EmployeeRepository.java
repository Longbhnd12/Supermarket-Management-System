package com.sms.backend.repository;

import com.sms.backend.entity.Employee;
import com.sms.backend.enums.GeneralStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EmployeeRepository extends JpaRepository<Employee, Long> {

    Optional<Employee> findByEmployeeCode(String employeeCode);

    boolean existsByEmployeeCode(String employeeCode);

    boolean existsByEmail(String email);

    List<Employee> findByStatus(GeneralStatus status);

    @Query("SELECT DISTINCT e.position FROM Employee e WHERE e.position IS NOT NULL AND TRIM(e.position) <> '' ORDER BY e.position ASC")
    List<String> findDistinctPositions();
}
