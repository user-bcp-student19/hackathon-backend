package com.bcp.hackathon.api;

public record ApiResult(int status, Object body) {
    public static ApiResult ok(Object body) {
        return new ApiResult(200, body);
    }

    public static ApiResult created(Object body) {
        return new ApiResult(201, body);
    }

    public static ApiResult accepted(Object body) {
        return new ApiResult(202, body);
    }

    public static ApiResult error(int status, String code) {
        return new ApiResult(status, java.util.Map.of("error", code));
    }
}
