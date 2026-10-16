package com.bcp.hackathon.api;

public class ApiException extends RuntimeException {
    private final int status;
    private final transient Object body;

    public ApiException(int status, Object body) {
        super(null, null, false, false);
        this.status = status;
        this.body = body;
    }

    public int status() {
        return status;
    }

    public Object body() {
        return body;
    }
}
