package com.datalinkx.dataclient.config;

import java.util.concurrent.TimeUnit;

import lombok.extern.slf4j.Slf4j;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import org.apache.commons.lang3.StringUtils;
import retrofit2.Retrofit;
import retrofit2.converter.JacksonParamConverterFactory;
import retrofit2.converter.jackson.JacksonConverterFactory;


//工具类 DatalinkXClientUtils，主要用于通过 Retrofit 创建 HTTP 客户端

@Slf4j
public final class DatalinkXClientUtils {

    //连接超时时间、调用超时时间和读取超时时间
    private static final long DEFAULT_CONNECT_TIMEOUT = 60000;
    private static final long DEFAULT_CALL_TIMEOUT = 60000;
    private static final long DEFAULT_READ_TIMEOUT = 60000;

    //定义了一个私有构造函数，防止外部直接实例化该工具类
    private DatalinkXClientUtils() {

    }


    public static <T> T createClient(String serviceName, ClientConfig.ServicePropertieBean properties, Class<T> clazz) {
        Retrofit retrofit = checkAndBuildRetrofit(serviceName, properties);
        return create(retrofit, clazz);
    }

    public static <T> T createClient(String serviceName, ClientConfig.ServicePropertieBean properties, Class<T> clazz,
                                     Interceptor interceptor) {
        Retrofit retrofit = checkAndBuildRetrofit(serviceName, properties, interceptor);
        return create(retrofit, clazz);
    }

    public static <T> T create(Retrofit retrofit, Class<T> serviceClazz) {
        return retrofit.create(serviceClazz);
    }

    //构建 Retrofit 实例

    private static Retrofit checkAndBuildRetrofit(String name, ClientConfig.ServicePropertieBean prop) {
//        LOGGER.info("config {} client:{}", name, prop);
        // check 参数
        if (prop != null) {
            if (StringUtils.isEmpty(prop.getUrl())) {
                log.error(name + " url required");
            }
        }

        //创建一个 OkHttpClient.Builder 实例，用于配置 HTTP 客户端
        OkHttpClient.Builder okHttpBuider = new OkHttpClient.Builder()
                .connectTimeout(
                        prop.getConnectTimeoutMs() != null ? prop.getConnectTimeoutMs() : DEFAULT_CONNECT_TIMEOUT,
                        TimeUnit.MILLISECONDS)
                .callTimeout(prop.getCallTimeoutMs() != null ? prop.getCallTimeoutMs() : DEFAULT_CALL_TIMEOUT,
                        TimeUnit.MILLISECONDS)
                .readTimeout(prop.getReadTimeoutMs() != null ? prop.getReadTimeoutMs() : DEFAULT_READ_TIMEOUT,
                        TimeUnit.MILLISECONDS);
        if (Boolean.TRUE.equals(prop.getLogging())) {
            HttpLoggingInterceptor logInterceptor = new HttpLoggingInterceptor();
            logInterceptor.setLevel(HttpLoggingInterceptor.Level.BODY);
            okHttpBuider.addInterceptor(logInterceptor);
        }

        //创建 Retrofit 构建器，设置基础 URL 和 HTTP 客户端
        /*
        addConverterFactory(JacksonConverterFactory.create())：添加 Jackson 转换器工厂，用于处理 JSON 数据。
        addConverterFactory(JacksonParamConverterFactory.create())：添加另一个 Jackson 转换器工厂，可能是用于处理特定的参数转换
         */
        return new Retrofit.Builder().baseUrl(prop.getUrl()).client(okHttpBuider.build())
                .addConverterFactory(JacksonConverterFactory.create())
                .addConverterFactory(JacksonParamConverterFactory.create())
                .addCallAdapterFactory(SynchronousCallAdapterFactory.create(prop.getErrorThrow())).build();
    }

    private static Retrofit checkAndBuildRetrofit(String name, ClientConfig.ServicePropertieBean prop, Interceptor interceptor) {
//        LOGGER.info("config {} client:{}", name, prop);
        // check 参数
        if (prop != null) {
            if (StringUtils.isEmpty(prop.getUrl())) {
                log.error(name + " url required");
            }
        }

        OkHttpClient.Builder okHttpBuider = new OkHttpClient.Builder()
                .connectTimeout(
                        prop.getConnectTimeoutMs() != null ? prop.getConnectTimeoutMs() : DEFAULT_CONNECT_TIMEOUT,
                        TimeUnit.MILLISECONDS)
                .callTimeout(prop.getCallTimeoutMs() != null ? prop.getCallTimeoutMs() : DEFAULT_CALL_TIMEOUT,
                        TimeUnit.MILLISECONDS)
                .readTimeout(prop.getReadTimeoutMs() != null ? prop.getReadTimeoutMs() : DEFAULT_READ_TIMEOUT,
                        TimeUnit.MILLISECONDS);
        if (Boolean.TRUE.equals(prop.getLogging())) {
            HttpLoggingInterceptor logInterceptor = new HttpLoggingInterceptor();
            logInterceptor.setLevel(HttpLoggingInterceptor.Level.BODY);
            okHttpBuider.addInterceptor(logInterceptor);
        }

        if (null != interceptor) {
            okHttpBuider.addInterceptor(interceptor);
        }

        return new Retrofit.Builder().baseUrl(prop.getUrl()).client(okHttpBuider.build())
                .addConverterFactory(JacksonConverterFactory.create())
                .addConverterFactory(JacksonParamConverterFactory.create())
                .addCallAdapterFactory(SynchronousCallAdapterFactory.create(prop.getErrorThrow())).build();
    }
}
