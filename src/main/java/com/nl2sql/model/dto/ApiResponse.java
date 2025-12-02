package com.nl2sql.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 统一API响应
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApiResponse<T> {
    
    private Boolean success;
    private String message;
    private T data;
    private String error;
    
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, "操作成功", data, null);
    }
    
    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>(true, message, data, null);
    }
    
    public static <T> ApiResponse<T> error(String error) {
        return new ApiResponse<>(false, null, null, error);
    }
    
    public static <T> ApiResponse<T> error(String message, String error) {
        return new ApiResponse<>(false, message, null, error);
    }
}
