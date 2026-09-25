package com.qingzhou.common.exception;

import com.qingzhou.common.api.R;
import com.qingzhou.common.api.ResultCode;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.ResourceAccessException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BizException.class)
    public R<Void> handleBiz(BizException ex) {
        log.warn("业务异常 code={} msg={}", ex.getCode(), ex.getMessage());
        return R.fail(ex.getCode(), ex.getMessage());
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    public R<Void> handleValid(Exception ex) {
        String msg = "参数校验失败";
        if (ex instanceof MethodArgumentNotValidException manv && manv.getBindingResult().getFieldError() != null) {
            msg = manv.getBindingResult().getFieldError().getDefaultMessage();
        } else if (ex instanceof BindException be && be.getBindingResult().getFieldError() != null) {
            msg = be.getBindingResult().getFieldError().getDefaultMessage();
        }
        return R.fail(ResultCode.BAD_REQUEST, msg);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public R<Void> handleConstraint(ConstraintViolationException ex) {
        return R.fail(ResultCode.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public R<Void> handleUnreadable(HttpMessageNotReadableException ex) {
        return R.fail(ResultCode.BAD_REQUEST, "请求体解析失败");
    }

    /** 第三方 HTTP 超时（后续 RestClient 调用会落到这里） */
    @ExceptionHandler(ResourceAccessException.class)
    public R<Void> handleTimeout(ResourceAccessException ex) {
        log.error("第三方调用超时", ex);
        return R.fail(ResultCode.THIRD_PARTY_TIMEOUT);
    }

    @ExceptionHandler(Exception.class)
    public R<Void> handleUnknown(Exception ex) {
        log.error("未处理异常", ex);
        return R.fail(ResultCode.SERVER_ERROR);
    }
}
