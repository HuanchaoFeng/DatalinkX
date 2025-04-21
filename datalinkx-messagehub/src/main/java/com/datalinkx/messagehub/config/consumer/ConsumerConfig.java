package com.datalinkx.messagehub.config.consumer;

import java.lang.reflect.Method;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import javax.annotation.Resource;

import com.datalinkx.messagehub.bean.form.ConsumerAdapterForm;
import com.datalinkx.messagehub.config.annotation.MessageHub;
import com.datalinkx.messagehub.service.MessageHubService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.ReflectionUtils;

/**
 * 这段代码定义了一个名为 ConsumerConfig 的类
 * 它实现了 Spring 的 BeanPostProcessor 和 EnvironmentAware 接口。
 * 它的主要功能是通过 Spring 的 Bean 生命周期管理机制，自动发现并初始化带有 @MessageHub 注解的消费者方法，
 * 并将这些消费者方法注册到 MessageHubService 中。
 * 如果初始化失败，它会将失败的消费者方法放入一个阻塞队列中，并启动一个后台线程定期重试。
 */

@Slf4j
@Component
public class ConsumerConfig implements BeanPostProcessor, EnvironmentAware {

    //BeanPostProcessor：用于在 Bean 初始化前后进行处理。
    //EnvironmentAware：用于获取 Spring 的 Environment 对象

    Environment environment;

    @Resource(name = "messageHubServiceImpl")
    MessageHubService messageHubService;

    @Resource(name = "resetBlockingQueue")
    BlockingQueue<ConsumerAdapterForm> resetBlockingQueue;//用于存储初始化失败的消费者方法，以便后续重试


    @Bean
    public BlockingQueue<ConsumerAdapterForm> resetBlockingQueue() {
        return new LinkedBlockingQueue<>();
    }



    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
        return bean;
    }


    //postProcessAfterInitialization的主要目的是对已经初始化完成的 Bean 进行进一步的处理
    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName)  throws BeansException {
        Method[] methods = ReflectionUtils.getAllDeclaredMethods(bean.getClass());//获取 Bean 类中声明的所有方法
        for (Method method : methods) { //检查方法是否带有 @MessageHub 注解
            MessageHub annotation = AnnotationUtils.findAnnotation(method, MessageHub.class);
            if (annotation == null) {
                continue;
            }

            log.info("discovery messagehub consumer bean:  class {}#{}", beanName, method.getName());

            // 1、解析topic是否配置在配置文件中，不存在返回原字符串
            //获取 @MessageHub 注解中 topic 属性的值
            /**
             * Spring 的 Environment 提供的方法，用于解析占位符。
             * 如果 annotation.topic() 返回的是一个占位符（例如 ${topic.name}），
             * resolvePlaceholders 会尝试从配置文件中解析出对应的值。
             * 如果配置文件中没有找到对应的值，则返回占位符的原始字符串。
             */
            String resolveTopic = environment.resolvePlaceholders(annotation.topic());

            ConsumerAdapterForm adapterForm = new ConsumerAdapterForm();
            adapterForm.setBean(bean);
            adapterForm.setInvokeMethod(method);
            adapterForm.setTopic(resolveTopic);
            adapterForm.setType(annotation.type());
            adapterForm.setGroup(annotation.group());
            this.consumerProxy(adapterForm);
        }
        return bean;
    }



    private void consumerProxy(ConsumerAdapterForm messageForm) {
        try {
            messageHubService.consume(messageForm);//尝试将消费者方法注册到 MessageHubService 中
        } catch (Exception e) {
            //如果注册失败，将消费者方法放入阻塞队列中，以便后续重试。
            log.warn("messagehub consumer init failed: type {}, topic {} , class {}#{}", messageForm.getType(), messageForm.getTopic(), messageForm.getBean().getClass().getName(), messageForm.getInvokeMethod().getName());
            log.warn("we will retry some times", e);
            resetBlockingQueue.add(messageForm);
        }
    }



    @Override
    public void setEnvironment(Environment environment) {
        //创建一个后台线程，用于定期重试初始化失败的消费者方法
        Thread healthThread = new Thread(new ConsumerInitRetryTask(messageHubService, resetBlockingQueue));
        healthThread.setDaemon(true);
        healthThread.start();

        this.environment = environment;
    }
}
