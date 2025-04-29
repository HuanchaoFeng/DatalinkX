package com.datalinkx.datajob.job;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import com.datalinkx.common.constants.MessageHubConstants;
import com.datalinkx.common.constants.MetaConstants;
import com.datalinkx.common.exception.DatalinkXJobException;
import com.datalinkx.common.result.WebResult;
import com.datalinkx.common.utils.IdUtils;
import com.datalinkx.common.utils.JsonUtils;
import com.datalinkx.datajob.action.AbstractDataTransferAction;
import com.datalinkx.datajob.action.DataTransferAction;
import com.datalinkx.datajob.action.StreamDataTransferAction;
import com.datalinkx.datajob.action.TransformDataTransferAction;
import com.datalinkx.datajob.bean.JobExecCountDto;
import com.datalinkx.datajob.bean.JobStateForm;
import com.datalinkx.datajob.bean.XxlJobParam;
import com.datalinkx.datajob.client.datalinkxserver.DatalinkXServerClient;
import com.datalinkx.driver.model.DataTransJobDetail;
import com.datalinkx.messagehub.service.redis.RedisPubSubProcessor;
import com.datalinkx.messagehub.service.redis.RedisQueueProcessor;
import com.datalinkx.messagehub.service.redis.RedisStreamProcessor;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.SneakyThrows;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.util.ObjectUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.PostConstruct;

/**
 * 任务由调度中心通过netty回调到DataTransHandler执行器中，执行器注入DataTransferAction这个任务触发类
 * 由doAction开始执行一次任务的执行，而FlinkAction是继承AbstractDataTransferAction实现各种模板和钩子方法
 */

/**
 * Data Trans Job Handler
 */
@RestController
@RequestMapping("/data/transfer")
public class DataTransHandler {
    private static Logger logger = LoggerFactory.getLogger(DataTransHandler.class);

    @Autowired
    private DataTransferAction dataTransferAction;

    @Autowired(required = false)
    private TransformDataTransferAction transformDataTransferAction;

    @Autowired
    private StreamDataTransferAction streamDataTransferAction;

    @Autowired
    private DatalinkXServerClient dataServerClient;//远程调用

    public Map<Integer, AbstractDataTransferAction> actionEngine = new ConcurrentHashMap<>();
    @PostConstruct
    public void init() {
        this.actionEngine.put(MetaConstants.JobType.JOB_TYPE_BATCH, dataTransferAction);

        if (!ObjectUtils.isEmpty(transformDataTransferAction)) {
            // 配置了seatunnel client后加载计算引擎
            this.actionEngine.put(MetaConstants.JobType.JOB_TYPE_COMPUTE, transformDataTransferAction);
        }
    }

    //根据任务 ID 获取任务详情
    public DataTransJobDetail getJobDetail(String jobId) {
        return dataServerClient.getJobExecInfo(jobId).getResult();
    }

    @SneakyThrows
    @RequestMapping("/stream_exec")
    public String streamJobHandler(String detail) { //处理流式数据传输任务的方法
        DataTransJobDetail dataTransJobDetail = JsonUtils.toObject(detail, DataTransJobDetail.class);
        streamDataTransferAction.doAction(dataTransJobDetail);
        return dataTransJobDetail.getJobId();
    }

    @RequestMapping("/stream_health")
    public WebResult<String> streamHealth(String jobId) { //定义了一个检查流式数据传输任务健康状况的请求映射
        // 如果因为datalinkx挂掉后重启，flink任务正常，datalinkx任务状态正常，判断健康检查线程是否挂掉, 如果挂掉，先停止再重新提交
        Set<Thread> threadsSet = Thread.getAllStackTraces().keySet();
        List<Thread> healthThreads = threadsSet.stream()
                .filter(Thread::isAlive)
                .filter(th -> th.getName().equals(IdUtils.getHealthThreadName(jobId)))
                .collect(Collectors.toList());

        if (ObjectUtils.isEmpty(healthThreads)) {
            return WebResult.of("");
        }
        return WebResult.of(healthThreads.get(0).getName());
    }

    /**
     * data trans job 任务执行器
     */
    @XxlJob("dataTransJobHandler")
    public void dataTransJobHandler() throws InterruptedException {
        XxlJobHelper.log("begin dataTransJobHandler. ");
        XxlJobParam jobParam = JsonUtils.toObject(XxlJobHelper.getJobParam(), XxlJobParam.class);
        String jobId = jobParam.getJobId();

        // 定时异步调用无法统一trace_id，这里用job_id做trace_id
        MDC.put("trace_id", new Date().getTime() + ":" + jobId);

        long startTime = new Date().getTime();
        DataTransJobDetail jobDetail;//存储任务的详细信息
        try {
            jobDetail = this.getJobDetail(jobId);
            //从 actionEngine 映射中获取与任务类型对应的执行引擎
            AbstractDataTransferAction engine = this.actionEngine.get(jobDetail.getType());
            if (ObjectUtils.isEmpty(engine)) {
                throw new DatalinkXJobException("引擎加载失败，检查配置!");
            }
            engine.doAction(jobDetail);//调用执行引擎的 doAction 方法执行任务
        } catch (InterruptedException e) {
            // cancel job
            throw e;
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            this.shutdownJob(startTime, jobId, e.getMessage());

            XxlJobHelper.handleFail(e.getMessage());
        }

        XxlJobHelper.log("end dataTransJobHandler. ");
        // default success 调用 XXL-JOB 提供的工具方法标记任务成功
        XxlJobHelper.handleSuccess("success");
    }

    private void shutdownJob(long startTime, String jobId, String message) {
        JobExecCountDto jobExecCountDto = new JobExecCountDto();
        dataServerClient.updateJobStatus(JobStateForm.builder().jobId(jobId)
                .jobStatus(MetaConstants.JobStatus.JOB_STATUS_ERROR).startTime(startTime).endTime(new Date().getTime())
                .errmsg(message).allCount(jobExecCountDto.getAllCount())
                .appendCount(jobExecCountDto.getAppendCount())
                .build());
    }
}
