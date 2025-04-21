package com.datalinkx.messagehub.config.topic;

import java.util.Timer;

import javax.annotation.Resource;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


@Configuration
@ConditionalOnProperty(prefix = "topic",name = "daemon",havingValue = "true")
@Slf4j
public class TopicDaemonWarden implements InitializingBean {

    @Resource
    TopicReloadTask topicReloadTask;


    @Bean
    public void topicReload() {
        Timer timer = new Timer();
        log.info("topic daemon warden reload start");
        //使用 Timer 调度 topicReloadTask 任务。任务将在 60 秒后首次执行，之后每隔 60 秒执行一次。
        timer.schedule(topicReloadTask, 60000, 60000);
    }

    /**
     *  afterPropertiesSet：这是 InitializingBean 接口的方法，它在 Spring 初始化 bean 的属性后被调用。
     * topicReloadTask.run();：在 bean 初始化后立即执行 topicReloadTask 任务。
     *
     */

    @Override
    public void afterPropertiesSet() throws Exception {
        topicReloadTask.run();
    }
}
