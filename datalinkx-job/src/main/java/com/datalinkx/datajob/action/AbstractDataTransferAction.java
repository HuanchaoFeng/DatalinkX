
package com.datalinkx.datajob.action;

import static com.datalinkx.common.constants.MetaConstants.JobStatus.JOB_STATUS_ERROR;
import static com.datalinkx.common.constants.MetaConstants.JobStatus.JOB_STATUS_STOP;
import static com.datalinkx.common.constants.MetaConstants.JobStatus.JOB_STATUS_SUCCESS;

import java.lang.reflect.Field;
import java.util.Map;

import com.datalinkx.common.constants.MetaConstants;
import com.datalinkx.common.utils.IdUtils;
import com.datalinkx.datajob.bean.JobExecCountDto;
import com.datalinkx.driver.model.DataTransJobDetail;
import com.xxl.job.core.thread.JobThread;
import lombok.extern.slf4j.Slf4j;



@Slf4j
public abstract class AbstractDataTransferAction<T extends DataTransJobDetail, U> {
    //更新后端db中的任务状态为进行中，任务统计值初始化
    protected abstract void begin(T info);
    //任务结束，累计任务统计值并更新后端db中的任务状态为成功或失败和统计任务条数
    protected abstract void end(U unit, int status, String errmsg);
    //任务开始前，构造reader、writer信息
    protected abstract void beforeExec(U unit) throws Exception;
    //任务执行，向flink提交任务
    protected abstract void execute(U unit) throws Exception;

    protected abstract boolean checkResult(U unit);
    //任务执行后，记录增量信息，累加任务统计值
    protected abstract void afterExec(U unit, boolean success);

    protected abstract U convertExecUnit(T info) throws Exception;

    private boolean isStop() {
        if (!(Thread.currentThread() instanceof  JobThread)) {
            return false;
        }
        JobThread jobThread = ((JobThread)Thread.currentThread());
        Field toStopField;
        boolean toStop = false;
        try {
            toStopField = jobThread.getClass().getDeclaredField("toStop");
            toStopField.setAccessible(true);
            try {
                toStop = toStopField.getBoolean(jobThread);
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }
        } catch (NoSuchFieldException e) {
            e.printStackTrace();
        }

        return toStop;
    }

    //doaction方法将上面的6个钩子方法（datalinkx任务的完整生命周期串起来）
    public void doAction(T actionInfo) throws Exception {
        Thread taskCheckerThread;
        // T -> U 获取引擎执行类对象
        U execUnit = convertExecUnit(actionInfo);
        try {
            StringBuffer error = new StringBuffer();
            // 1、准备执行job
            this.begin(actionInfo);
            //从 DataTransferAction.COUNT_RES 中获取任务统计值的存储容器
            Map<String, JobExecCountDto> countRes = DataTransferAction.COUNT_RES.get();

            String healthCheck = "patch-data-job-check-thread";
            if (MetaConstants.DsType.STREAM_DB_LIST.contains(actionInfo.getSyncUnit().getReader().getType())) {
                healthCheck = IdUtils.getHealthThreadName(actionInfo.getJobId());
            }

            // 3、循环检查任务结果 创建一个线程 taskCheckerThread，用于循环检查任务是否执行完成。
            /**
             * 如果任务完成，调用 afterExec 方法进行后置处理，并退出循环
             * 如果任务执行过程中出现异常，记录错误信息，调用 afterExec 方法进行后置处理，并退出循环
             */
            taskCheckerThread = new Thread(() -> {
                DataTransferAction.COUNT_RES.set(countRes);

                while (true) {
                    try {
                        // 3.1、如果任务执行完成
                        if (checkResult(execUnit)) {
                            // 3.2、执行任务后置处理钩子
                            this.afterExec(execUnit, true);
                            break;
                        }
                        Thread.sleep(5000);
                    } catch (Exception e) {
                        log.error("data-transfer-job error ", e);
                        String errorMsg = e.getMessage();
                        error.append(errorMsg).append("\r\n");
                        log.info(errorMsg);
                        this.afterExec(execUnit, false);
                        break;
                    }
                }
                DataTransferAction.COUNT_RES.remove();
            }, healthCheck);

            // 4、向引擎提交任务
            try {
                // 4.1、是否用户取消任务
                if (isStop()) {
                    log.error("job shutdown trigger");
                    throw new InterruptedException();
                }

                // 4.2、每个单元执行前的准备
                this.beforeExec(execUnit);

                // 4.3、启动任务
                this.execute(execUnit);
            } catch (InterruptedException e) {
                // 用户手动取消任务
                throw e;
            } catch (Throwable e) {
                //如果任务提交过程中出现异常，记录错误信息，调用 afterExec 方法进行后置处理，并更新任务状态为“失败”
                log.error("execute task error.", e);
                afterExec(execUnit, false);
                error.append(e.getMessage()).append("\r\n");
                this.end(execUnit,  JOB_STATUS_ERROR, error.toString());
                return;
            }
            // 阻塞至任务完成
            taskCheckerThread.start();
            //join() 方法会阻塞当前线程，直到 taskCheckerThread 执行完毕（即 taskCheckerThread 的 run() 方法执行完成）
            taskCheckerThread.join();

            // 5、整个Job结束后的处理
            this.end(execUnit, error.length() == 0 ? JOB_STATUS_SUCCESS : JOB_STATUS_ERROR, error.length() == 0 ? "success" : error.toString());
        } catch (InterruptedException e) {
            log.error("shutdown job by user.");
            this.end(execUnit, JOB_STATUS_STOP, "cancel the job");
            throw e;
        } catch (Throwable e) {
            log.error("transfer failed -> ", e);
            this.end(execUnit, JOB_STATUS_ERROR, e.getMessage());
        }
    }
}
