package com.datalinkx.sse.config;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import com.datalinkx.common.utils.ObjectUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Slf4j
public class SseEmitterServer {

    /**
     * 当前连接数
     */
    private static final AtomicInteger count = new AtomicInteger(0);

    /**
     * 使用map对象，便于根据userId来获取对应的SseEmitter，或者放redis里面
     */
    private static final Map<String, SseEmitter> sseEmitterMap = new ConcurrentHashMap<>();

    /**
     * 创建用户连接并返回 SseEmitter
     * @param employeeCode 用户ID
     * @return SseEmitter
     */
    public static SseEmitter connect(String employeeCode) {
        if (!ObjectUtils.isEmpty(sseEmitterMap.get(employeeCode))) {
            //检查是否已经存在一个与该用户ID对应的 SseEmitter 对象。如果存在，则直接返回该对象，避免重复创建
            return sseEmitterMap.get(employeeCode);
        }
        SseEmitter sseEmitter = new SseEmitter(0L);//参数 0L 表示没有超时时间限制
        /**
         * onCompletion：连接完成时的回调
         * onError：发生错误时的回调
         * onTimeout：连接超时时的回调
         */
        // 注册回调
        sseEmitter.onCompletion(completionCallBack(employeeCode));
        sseEmitter.onError(errorCallBack(employeeCode));
        sseEmitter.onTimeout(timeoutCallBack(employeeCode));
        sseEmitterMap.put(employeeCode, sseEmitter);
        // 数量+1
        count.getAndIncrement();
        return sseEmitter;
    }

    /**
     * 给指定用户发送信息
     * @param employeeCode
     * @param jsonMsg
     */
    public static void sendMessage(String employeeCode, String jsonMsg) {
        try {
            SseEmitter emitter = sseEmitterMap.get(employeeCode);//从 sseEmitterMap 中获取与用户ID对应的 SseEmitter 对象
            if (emitter == null) {
                emitter = connect(employeeCode);
            }
            emitter.send(jsonMsg, MediaType.APPLICATION_JSON);
        } catch (IOException e) {
            log.error("sse用户[{}]推送异常:", employeeCode, e);
            removeUser(employeeCode);
        }
    }


    /**
     * 移除用户连接
     */
    public static void removeUser(String employeeCode) {
        SseEmitter emitter = sseEmitterMap.get(employeeCode);
        if(emitter != null){
            emitter.complete();
        }
        sseEmitterMap.remove(employeeCode);
        // 数量-1
        count.getAndDecrement();
    }



    private static Runnable completionCallBack(String employeeCode) {
        //当连接完成时，记录日志并调用 removeUser 方法移除该用户的连接
        return () -> {
            log.info("end sse connect：{}", employeeCode);
            removeUser(employeeCode);
        };
    }

    private static Runnable timeoutCallBack(String employeeCode) {
        //当连接超时时，记录日志并调用 removeUser 方法移除该用户的连接
        return () -> {
            log.info("connect sse connect timeout：{}", employeeCode);
            removeUser(employeeCode);
        };
    }

    private static Consumer<Throwable> errorCallBack(String employeeCode) {
        //当发生错误时，记录日志并调用 removeUser 方法移除该用户的连接
        return throwable -> {
            log.info("sse connect error：{}", employeeCode);
            removeUser(employeeCode);
        };
    }
}
