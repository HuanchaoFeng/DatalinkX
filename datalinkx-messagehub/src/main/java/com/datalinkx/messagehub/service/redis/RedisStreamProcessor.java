package com.datalinkx.messagehub.service.redis;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

import com.datalinkx.messagehub.bean.form.ConsumerAdapterForm;
import com.datalinkx.messagehub.bean.form.ProducerAdapterForm;
import com.datalinkx.messagehub.service.MessageHubServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.ObjectRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamInfo;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;
import org.yaml.snakeyaml.tokens.ScalarToken;


@Slf4j
@Scope(proxyMode = ScopedProxyMode.TARGET_CLASS)
@Service("redisStreamProcessor")
public class RedisStreamProcessor extends MessageHubServiceImpl {


    @Override
    public void produce(ProducerAdapterForm producerAdapterForm) {
        ObjectRecord<String, Object> stringObjectRecord = ObjectRecord.create(producerAdapterForm.getTopic(), producerAdapterForm.getMessage());
        stringRedisTemplate.opsForStream().add(stringObjectRecord);//将消息发送到 Redis Stream 中
    }

    @Override
    public void consume(ConsumerAdapterForm messageForm) {

        String topic = messageForm.getTopic();
        String group = messageForm.getGroup();
        String consumerName = messageForm.getInvokeMethod().getName();
        Object consumerBean = messageForm.getBean();
        Method invokeMethod = messageForm.getInvokeMethod();

        if (ObjectUtils.isEmpty(group)) {
            throw new Error("REDIS_STREAM消费类型未指定消费者组");
        }

        //使用 new Thread().start() 创建并启动了一个新线程的原因是 实现异步消费逻辑
        //其实注册的逻辑就是开启一个线程去接收信息
        //消费逻辑是通过一个无限循环实现的，每次循环都会从 Redis Stream 中读取消息并处理。
        // 这种模式需要持续运行，不能阻塞主线程，因此需要在一个独立的线程中执行。
        new Thread(() -> {
            //lastOffset：用于记录上一次成功处理的消息的偏移量（Offset）。在 Redis Stream 中，偏移量用于标记消息的位置。
            String lastOffset = null;

            StreamOperations<String, String, Object> streamOperations = this.stringRedisTemplate.opsForStream();

            if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(topic))) {//检查 Redis 中是否存在指定的主题（Stream）
                StreamInfo.XInfoGroups groups = streamOperations.groups(topic);//获取指定主题的所有消费者组信息

                AtomicReference<Boolean> groupHasKey = new AtomicReference<>(false);//用于线程安全地检查消费者组是否存在

                groups.forEach(groupInfo -> { //遍历所有消费者组，检查是否存在指定的消费者组
                    if (Objects.equals(group, groupInfo.getRaw().get("name"))) {
                        groupHasKey.set(true);
                    }
                });

                if (groups.isEmpty() || !groupHasKey.get()) {
                    String groupName = streamOperations.createGroup(topic, group);//如果消费者组不存在，则创建一个新的消费者组，并记录日志
                    log.info("messagehub stream creatGroup:{}", groupName);
                }
            } else {
                /**
                 * 如果 Redis 中不存在指定的主题（Stream），则直接创建一个新的消费者组。
                 * 这里假设在创建消费者组时，Spring Data Redis 会自动创建主题（Stream）。
                 */
                String groupName = streamOperations.createGroup(topic, group);
                log.info("messagehub stream creatGroup:{}", groupName);
            }

            while (true) {
                try {
                    //ReadOffset.lastConsumed()：获取从上次消费的位置开始读取消息的偏移量。
                    // 这是 Redis Stream 的一个特性，允许消费者从上次消费的位置继续读取
                    ReadOffset readOffset = ReadOffset.lastConsumed();
                    /**
                     * streamOperations.read(...)：从 Redis Stream 中读取消息。
                     * String.class：指定消息的值类型为 String。
                     * Consumer.from(group, consumerName)：指定消费者组和消费者名称。
                     * StreamReadOptions.empty().count(1).block(Duration.ofSeconds(5))：设置读取选项：
                     * count(1)：每次最多读取 1 条消息。
                     * block(Duration.ofSeconds(5))：如果当前没有消息，阻塞等待最多 5 秒。
                     * StreamOffset.create(topic, readOffset)：指定读取的 Stream 和偏移量
                     */
                    List<ObjectRecord<String, String>> messageList = streamOperations.read(
                            String.class,
                            Consumer.from(group, consumerName),
                            StreamReadOptions.empty().count(1).block(Duration.ofSeconds(5)),
                            StreamOffset.create(topic, readOffset));


                    if (ObjectUtils.isEmpty(messageList)) {//检查是否读取到消息。如果 messageList 为空，说明没有消息
                        // 如果为null，说明没有消息，继续下一次循环
                        Thread.sleep(1000);
                        continue;
                    }

                    for (ObjectRecord<String, String> record : messageList) {
                        lastOffset = record.getId().getValue();
                        //调用消费者方法，处理消息。consumerBean 是包含消费者方法的 Bean 实例，record.getValue() 是消息的内容
                        //invokemethod是consumerconfig里面找到的带有@messageHub的方法，也就是消费者方法
                        invokeMethod.invoke(consumerBean, record.getValue());
                        //确认消息已被处理。这是 Redis Stream 的一个特性，允许消费者确认消息已被成功处理，避免重复消费
                        stringRedisTemplate.opsForStream().acknowledge(group, record);
                    }
                } catch (Throwable e) {
                    //这里关于消息异常的情况，其实没有很好的处理，异常消息会在pendinglist中，应该要进行处理
                    log.error("messagehub stream consumer {} consume error, last offset: {}", consumerName, lastOffset);
                    log.error(e.getMessage(), e);
                    try {
                        Thread.sleep(3000);
                    } catch (InterruptedException ex) {
                        ex.printStackTrace();
                    }
                }
            }
        }).start();
    }
}
