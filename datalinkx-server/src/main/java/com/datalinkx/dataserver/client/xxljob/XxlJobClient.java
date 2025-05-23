package com.datalinkx.dataserver.client.xxljob;

import java.util.Map;

import com.datalinkx.dataserver.client.xxljob.request.LogQueryParam;
import com.datalinkx.dataserver.client.xxljob.request.PageQueryParam;
import com.datalinkx.dataserver.client.xxljob.request.XxlJobInfo;
import com.datalinkx.dataserver.client.xxljob.response.ReturnT;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Field;
import retrofit2.http.FormUrlEncoded;
import retrofit2.http.POST;
import retrofit2.http.QueryBean;

/**
 * xxl-job service api interface
 */
public interface XxlJobClient {

    /*
    @FormUrlEncoded 是 Retrofit 的一个注解，用于指定 HTTP 请求的请求体是表单编码格式（application/x-www-form-urlencoded）
    这种格式将请求参数以键值对的形式编码在请求体中，例如：key1=value1&key2=value2
     */

    @FormUrlEncoded
    @POST("/xxl-job-admin/jobinfo/add")
    ReturnT<String> add(@QueryBean XxlJobInfo xxlJobInfo);

    @FormUrlEncoded
    @POST("/xxl-job-admin/jobinfo/update")
    ReturnT<String> update(@QueryBean XxlJobInfo xxlJobInfo);
    
    @FormUrlEncoded
    @POST("/xxl-job-admin/jobinfo/pageList")
    Map<String, Object> pageList(@QueryBean PageQueryParam queryParam);

    @FormUrlEncoded
    @POST("/xxl-job-admin/jobinfo/remove")
    ReturnT<String> remove(@Field("id") int id);

    @FormUrlEncoded
    @POST("/xxl-job-admin/jobinfo/start")
    ReturnT<String> start(@Field("id") int id);

    @FormUrlEncoded
    @POST("/xxl-job-admin/jobinfo/stop")
    ReturnT<String> stop(@Field("id") int id);

    @FormUrlEncoded
    @POST("/xxl-job-admin/jobinfo/trigger")
    ReturnT<String> trigger(@Field("id") int id, @Field("executorParam") String executorParam, @Field("addressList") String addressList);

    @FormUrlEncoded
    @POST("/xxl-job-admin/login")
    Call<ResponseBody> login(@Field("userName") String userName,
                             @Field("password") String password,
                             @Field("ifRemember") String ifRemember);

    @FormUrlEncoded
    @POST("/xxl-job-admin/joblog/pageList")
    Map<String, Object> logList(@QueryBean LogQueryParam logQueryParam);

    @FormUrlEncoded
    @POST("/xxl-job-admin/joblog/logKill")
    ReturnT<String> logKill(@Field("id") int id);
}
