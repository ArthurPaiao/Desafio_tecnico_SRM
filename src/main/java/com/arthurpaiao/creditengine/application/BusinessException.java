package com.arthurpaiao.creditengine.application;

public class BusinessException extends RuntimeException {
    public enum Code { NOT_FOUND, EXCHANGE_RATE_UNAVAILABLE, EXCHANGE_RATE_EXPIRED,
        OPERATION_IN_PROGRESS, ALREADY_SETTLED, IDEMPOTENCY_CONFLICT }
    private final Code code;

    public BusinessException(Code code, String message) {
        super(message);
        this.code = code;
    }
    public Code getCode() { return code; }
}
