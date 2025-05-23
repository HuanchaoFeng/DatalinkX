
package com.datalinkx.dataserver.client.xxljob.interceptor;

import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.Charset;

import com.datalinkx.dataserver.client.xxljob.XxlLoginClient;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Headers;
import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.Buffer;
import okio.BufferedSource;
import okio.GzipSource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;


@Slf4j
@Component
public class LoginInterceptor implements Interceptor {
	public static final String LOGIN_IDENTITY_KEY = "XXL_JOB_LOGIN_IDENTITY";
	private static final String HEADER_COOKIE = "Cookie";
	private static final String SET_COOKIE_HEADER = "Set-Cookie";

	private static String cookieValue = "";

	@Autowired
	private XxlLoginClient xxlLoginClient;

	@Value("${xxl-job.username}")
	private String username;

	@Value("${xxl-job.password}")
	private String passwd;

	public static LoginInterceptor create() {
		return new LoginInterceptor();
	}

	/**
	 *
	 * 在 OkHttp 中，拦截器可以应用于两种类型的调用：出站调用（从服务器发起到外部服务的调用）和入站调用（从外部服务发起到服务器的调用）
	 * 下面这种是出战调用
	 * @param chain
	 * @return
	 * @throws IOException
	 */
	@Override
	public Response intercept(Chain chain) throws IOException {
		Request request = chain.request();

		Response response;
		try {
			if (StringUtils.isEmpty(cookieValue)) {
				retrofit2.Response loginResp = xxlLoginClient.login(username, passwd, "on").execute();
				cookieValue = loginResp.headers().get(SET_COOKIE_HEADER);
			}

			if (StringUtils.isNotEmpty(cookieValue)) {//检查是否成功获取了 Cookie 值
				//如果获取了 Cookie，则创建一个新的请求，将 Cookie 添加到请求头中
				Request newRequest = chain.request().newBuilder()
						.addHeader(HEADER_COOKIE, cookieValue)
						.build();
				//使用新的请求继续执行链式调用，获取响应
				//当前拦截器已经完成了对请求的处理（如果有的话），将请求传递给拦截器链中的下一个拦截器继续处理
				// 最后一个拦截器处理请求后，不再调用 proceed，而是直接将请求发送到服务器。服务器的响应返回，按相反顺序通过拦截器链。
				response = chain.proceed(newRequest);
			} else {
				log.error("xxl-job login error");
				response = chain.proceed(chain.request());
			}
		} catch (Exception e) {
			throw e;
		}

		if (response.code() == 200) {
			ResponseBody responseBody = response.body();
			MediaType contentType = responseBody != null ? responseBody.contentType() : null;
			if (contentType != null && contentType.subtype().equals("json")) {
				String bodyString = getBody(response);
				bodyString = bodyString.replaceFirst("\"result\":\\s*\"\\s*\"", "\"result\": null");
				ResponseBody body = ResponseBody.create(contentType, bodyString);
				response = response.newBuilder().body(body).build();
				// HashMap<String, Object> jsonMap = gson.fromJson(bodyString, new
				// TypeToken<HashMap<String, Object>>() {
				// }.getType());
				// if (jsonMap.get("result") != null && jsonMap.get("result").equals("")) {
				// jsonMap.put("result", null);
				// ResponseBody body = ResponseBody.create(contentType, gson.toJson(jsonMap));
				// response = response.newBuilder().body(body).build();
				// }
			}
		}

		return response;
	}

	/**
	 * 获取body内容，部分代码引用HttpLoggingInterceptor
	 */
	private String getBody(Response response) throws IOException {
		ResponseBody responseBody = response.body();
		Headers headers = response.headers();
		BufferedSource source = responseBody.source();
		source.request(9223372036854775807L);
		Buffer buffer = source.getBuffer();
		if ("gzip".equalsIgnoreCase(headers.get("Content-Encoding"))) {
			GzipSource gzippedResponseBody = new GzipSource(buffer.clone());
			Throwable var24 = null;

			try {
				buffer = new Buffer();
				buffer.writeAll(gzippedResponseBody);
			} catch (Throwable var34) {
				var24 = var34;
				throw var34;
			} finally {
				if (var24 != null) {
					try {
						gzippedResponseBody.close();
					} catch (Throwable var33) {
						var24.addSuppressed(var33);
					}
				} else {
					gzippedResponseBody.close();
				}

			}
		}

		Charset charset = Charset.forName("UTF-8");
		MediaType contentType = responseBody.contentType();
		if (contentType != null) {
			charset = contentType.charset(Charset.forName("UTF-8"));
		}

		if (!isPlaintext(buffer)) {
			// Binary
			return "";
		}
		if (responseBody.contentLength() != 0L) {
			return buffer.clone().readString(charset);
		}

		return "";
	}

	static boolean isPlaintext(Buffer buffer) {
		try {
			Buffer prefix = new Buffer();
			long byteCount = buffer.size() < 64L ? buffer.size() : 64L;
			buffer.copyTo(prefix, 0L, byteCount);

			for (int i = 0; i < 16 && !prefix.exhausted(); ++i) {
				int codePoint = prefix.readUtf8CodePoint();
				if (Character.isISOControl(codePoint) && !Character.isWhitespace(codePoint)) {
					return false;
				}
			}

			return true;
		} catch (EOFException var6) {
			return false;
		}
	}
}
