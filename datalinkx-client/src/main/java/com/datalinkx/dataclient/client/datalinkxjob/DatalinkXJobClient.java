package com.datalinkx.dataclient.client.datalinkxjob;

import com.datalinkx.common.result.WebResult;
import retrofit2.http.POST;
import retrofit2.http.Query;

public interface DatalinkXJobClient {

    /*
    @Query 注解：将参数作为查询参数附加到 URL 中，而不是放在请求体中。例如，请求的 URL 可能类似于：/data/transfer/stream_exec?detail=someDetailValue
     */

    //dataTransExec(String dataTransJobDetail)：执行一个数据传输任务。
    @POST("/data/transfer/stream_exec")
    WebResult<String> dataTransExec(@Query("detail") String dataTransJobDetail);

    //streamJobHealth(String jobId)：检查流任务的健康状态。
    @POST("/data/transfer/stream_health")
    WebResult<String> streamJobHealth(@Query("jobId") String jobId);
}
