package com.adam.restaurantoperations.staff;

import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EmployeeRepository
        extends JpaRepository<EmployeeEntity, Long>, JpaSpecificationExecutor<EmployeeEntity> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select employee from EmployeeEntity employee where employee.id = :id")
    Optional<EmployeeEntity> findByIdForUpdate(@Param("id") Long id);
}
