package com.management.managementapi.dto.employee;

public class NotFoundException extends RuntimeException {
    public NotFoundException(String msg) { super(msg); }
}