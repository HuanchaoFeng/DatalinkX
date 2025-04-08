package com.datalinkx.dataserver.controller.advice;

import com.datalinkx.common.result.WebResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;



/**
 * 统一数据返回格式处理
 *
 * 使用了@ControllerAdvice注解，主要用于统一处理控制器返回的结果，将非WebResult类型的返回值包装为WebResult对象
 * basePackages = "com.datalinkx"，指定了该增强类只对com.datalinkx包及其子包中的控制器生效
 */
@Slf4j
@ControllerAdvice(basePackages = "com.datalinkx")
public class ResultControllerAdvice implements ResponseBodyAdvice<Object> {

	@Override
	public boolean supports(MethodParameter methodParameter, Class<? extends HttpMessageConverter<?>> aClass) {
		return true;
	}

	@Override
	public Object beforeBodyWrite(
			Object body,
			MethodParameter methodParameter,
			MediaType mediaType,
			Class<? extends HttpMessageConverter<?>> aClass,
			ServerHttpRequest req,
			ServerHttpResponse resp) {
		if (mediaType.compareTo(MediaType.APPLICATION_JSON) != 0) {
			//非JSON类型的响应（如HTML页面、文件下载等），直接返回body
			return body;
		}
		//body instanceof WebResult 是用来判断 body 是否是 WebResult 类型
		return (body instanceof WebResult) ? body : WebResult.of(body);
	}
}
