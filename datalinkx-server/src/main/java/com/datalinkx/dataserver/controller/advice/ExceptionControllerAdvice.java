package com.datalinkx.dataserver.controller.advice;


import java.util.stream.Collectors;

import javax.validation.ConstraintViolation;
import javax.validation.ConstraintViolationException;

import com.datalinkx.common.exception.DatalinkXServerException;
import com.datalinkx.common.result.WebResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * @ControllerAdvice
 * 用于定义一个全局的异常处理器类。
 * 它可以捕获控制器层抛出的异常，并通过 @ExceptionHandler 注解的方法来处理这些异常。
 * 可以指定该类只对某些包下的控制器生效，通过 basePackages 属性来限定。
 *
 * @ExceptionHandler
 * 作用：
 * 用于定义一个方法来处理特定类型的异常。
 * 可以指定一个或多个异常类型，当控制器方法抛出这些异常时，Spring 会自动调用这个方法来处理异常。
 *
* @Order(Ordered.HIGHEST_PRECEDENCE)：这个注解用于指定该异常处理器的优先级。
 * Ordered.HIGHEST_PRECEDENCE 表示该处理器具有最高的优先级，确保它会优先处理异常。
 *
 *
 */

@Slf4j
@ControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ExceptionControllerAdvice {

    @ResponseBody
    @ExceptionHandler(value = {IllegalStateException.class, IllegalArgumentException.class})
    public WebResult<?> handleException(Exception exception) throws Exception {
        return WebResult.fail(exception, null);
    }


    //指定该方法处理 MethodArgumentNotValidException，这通常是 Spring 的 @Valid 注解校验失败时抛出的异常
    @ResponseBody
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public WebResult<?> handleException(MethodArgumentNotValidException exception) {
        return WebResult.fail(exception, ErrorsUtils.compositeValiditionError(exception.getBindingResult()));
    }


    //指定该方法处理 MissingServletRequestParameterException，这通常是请求中缺少必需的参数时抛出的异常
    @ResponseBody
    @ExceptionHandler(MissingServletRequestParameterException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public WebResult<?> handleException(MissingServletRequestParameterException exception) throws Exception {
        return WebResult.fail(exception, exception.getParameterName());
    }



    /**
     * jpa层的校验
     *
     * @param exception
     * @return
     * @throws Exception
     */
    @ExceptionHandler(value = {ConstraintViolationException.class})
    public WebResult<?> validationException(ConstraintViolationException exception) throws Exception {

        /**
         * 从 ConstraintViolationException 中提取所有校验失败的 ConstraintViolation 对象
         * 当多个 ConstraintViolation 对象的 propertyPath 相同时（即同一个字段有多个校验失败），使用这个合并函数(m1, m2) -> m1 + m2来合并它们的错误信息。
         */
        return WebResult.fail(exception,
                exception.getConstraintViolations()
                        .stream()
                        .collect(Collectors.toMap(
                                ConstraintViolation::getPropertyPath,
                                ConstraintViolation::getMessage,
                                (m1, m2) -> m1 + m2))
        );
    }



	/**
	 * 统一处理自定义异样，根据状态码返回
	 */
	@ExceptionHandler({DatalinkXServerException.class})
	@ResponseBody
	public WebResult<?> databridgeExceptionHandler(DatalinkXServerException databridgeException) {
		Throwable r = ErrorsUtils.getRootCause(databridgeException);
        return WebResult.fail(r);
	}

    /**
     * 统一处理Controller中的异常 需要统一处理异常编码
     *
     * @param runtimeException
     * @return
     */
    @ExceptionHandler({RuntimeException.class})
    @ResponseBody
    public ResponseEntity<?> runtimeExceptionHandler(RuntimeException runtimeException) {
        Throwable r = ErrorsUtils.getRootCause(runtimeException);
        WebResult result = WebResult.fail(r);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .body(result);
    }


    @ResponseBody
    @ExceptionHandler(value = {Exception.class, Error.class})
    public WebResult<?> handleUncatchException(Throwable exception) {
        return WebResult.fail(exception, null);
    }
}
