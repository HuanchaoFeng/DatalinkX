package com.datalinkx.dataserver.config;



import java.io.IOException;
import java.util.UUID;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.datalinkx.common.constants.MetaConstants;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;



@Slf4j
@Component
public class RequestLogFilter implements Filter {

    @Override
    public void init(FilterConfig filterConfig) {

    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;

        /**
         *  Mapped Diagnostic Context (MDC) 中获取 TRACE_ID。TRACE_ID 是一个用于追踪请求的唯一标识符
         *  如果 MDC 中没有 TRACE_ID，则尝试从请求头或请求参数中获取
         */

        String traceId = MDC.get(MetaConstants.CommonConstant.TRACE_ID);
        if (!StringUtils.hasLength(traceId)) {
            //getCommonVariable 方法会先从请求头中获取，如果不存在，则从请求参数中获取
            traceId = getCommonVariable(request, MetaConstants.CommonConstant.TRACE_ID);
            if (!StringUtils.hasLength(traceId)) {
                traceId = UUID.randomUUID().toString();//如果仍然没有获取到 TRACE_ID，则生成一个新的 UUID 作为 TRACE_ID
            }
        }
        MDC.put(MetaConstants.CommonConstant.TRACE_ID, traceId);

        //将请求开始时间和 TRACE_ID 放入请求属性中，以便在后续处理中使用
        request.setAttribute("reqStartTime", System.currentTimeMillis());
        request.setAttribute("traceId", traceId);
        String realIp = request.getHeader("X-Real-Ip"); //从请求头中获取真实 IP 地址

        try {
            filterChain.doFilter(servletRequest, servletResponse);
        } finally {
            /**
             * 计算请求的处理时间
             * 调用 afterRequestLog 方法记录请求的日志
             * 清空 MDC，以避免线程池复用时的上下文污染
             */
            long elasTime = System.currentTimeMillis() - (long) request.getAttribute("reqStartTime");
            afterRequestLog(request, response, realIp, elasTime);
            MDC.clear();
        }
    }

    public String getCommonVariable(HttpServletRequest req, String name) {
        String value = req.getHeader(name);
        if (!StringUtils.hasLength(value)) {
            value = req.getParameter(name);
        }
        return value;
    }
    
    private void afterRequestLog(HttpServletRequest request, HttpServletResponse response, String realIp, long elasTime) {
        int normalStatus = 200;
        log.info("uri={}||http_method={}||real_ip={}||request_time={}||status={}||elapse={}", request.getRequestURI(), request.getMethod(),
                realIp, request.getAttribute("reqStartTime"), response.getStatus() == normalStatus ? 0 : 1, elasTime);
    }
    
    @Override
    public void destroy() {
    }
}