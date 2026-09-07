package com.management.managementapi.enterprises.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.management.managementapi.enterprises.model.Payment;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, UUID> {
}
