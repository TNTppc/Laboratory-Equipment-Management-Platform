package cn.labmanage.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    ResponseEntity<ApiResult<Void>> business(BusinessException e) { return ResponseEntity.badRequest().body(ApiResult.error(e.getMessage())); }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiResult<Void>> validation(MethodArgumentNotValidException e) {
        FieldError error = e.getBindingResult().getFieldErrors().stream().findFirst().orElse(null);
        return ResponseEntity.badRequest().body(ApiResult.error(error == null ? "请求参数不合法" : error.getDefaultMessage()));
    }
    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ApiResult<Void>> denied(AccessDeniedException e) { return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResult.error("无权执行该操作")); }
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ApiResult<Void>> typeMismatch(MethodArgumentTypeMismatchException e) { return ResponseEntity.badRequest().body(ApiResult.error("请求参数格式错误")); }
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<ApiResult<Void>> uploadTooLarge(MaxUploadSizeExceededException e) { return ResponseEntity.badRequest().body(ApiResult.error("图片不能超过 5MB")); }
    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiResult<Void>> unexpected(Exception e) { log.error("服务器处理请求失败", e); return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResult.error("服务器处理失败")); }
}
