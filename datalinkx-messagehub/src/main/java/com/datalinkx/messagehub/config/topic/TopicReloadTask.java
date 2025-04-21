package com.datalinkx.messagehub.config.topic;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TimerTask;
import java.util.stream.Collectors;

import javax.annotation.Resource;
import javax.persistence.EntityManager;
import javax.persistence.Query;

import com.datalinkx.common.constants.MessageHubConstants;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.SQLQuery;
import org.hibernate.transform.Transformers;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;


//TimerTask 是 Java 提供的一个类，用于定义一个可以被定时器调度的任务。这个类继承了 TimerTask，表示它是一个可以定时执行的任务

@Slf4j
@Service
@ConditionalOnProperty(prefix = "topic",name = "daemon",havingValue = "true")
public class TopicReloadTask extends TimerTask {

    @Resource
    StringRedisTemplate stringRedisTemplate;

    //EntityManager：用于操作数据库的 JPA 实体管理器
    @Resource
    EntityManager entityManager;

    public final String TOPIC_SQL = " select topic, info_type from MESSAGEHUB_TOPIC where is_del = 0 ";
    /**
     *   删除 Redis 中键名为 MessageHubConstants.WHITE_TOPIC 的集合
     *  local topics：定义了一个局部变量 topics
     *  KEYS是 Lua 脚本中传递给脚本的参数列表。
     *  在 Redis 脚本中，KEYS 是一个数组，包含调用脚本时传递的所有键名
     *  unpack 是 Lua 中的一个函数，用于将一个数组展开为多个参数。这里的作用是将 topics 数组中的每个主题名称作为参数传递给 SADD 命令
     */
    public final String LUA_SCRIPT =
                "redis.call('del', '" + MessageHubConstants.WHITE_TOPIC +  "')" +
                "local topics = KEYS " +
                "redis.call('SADD', '" + MessageHubConstants.WHITE_TOPIC + "', unpack(topics)) ";


    @Override
    public void run() {
        try {
            //调用 getQueryResult 方法，执行 SQL 查询，获取topic信息
            List<Map<String, Object>> topics = this.getQueryResult(TOPIC_SQL);
            if (!ObjectUtils.isEmpty(topics)) {

                List<String> mappingTopics = topics.stream().map(
                        v -> MessageHubConstants.getInnerTopicName(
                                (String) v.get("info_type"),
                                (String) v.get("topic")
                        )
                ).collect(Collectors.toList());

                //创建一个 Redis 脚本对象，用于执行 Lua 脚本
                DefaultRedisScript<Long> redisScript = new DefaultRedisScript<>(LUA_SCRIPT, Long.class);
                this.stringRedisTemplate.execute(redisScript, mappingTopics);
                log.info("messagehub topic reload finish");
            } else {
                log.warn("messagehub topic is empty");
            }
        } catch (Throwable t) {

            log.error("messagehub topic reload error", t);
        }
    }

    private <T> List<T> getQueryResult(String sql, Class<T> clazz) {
        Query dataQuery = this.entityManager.createNativeQuery(sql, clazz);
        List<T> result = new ArrayList<>();
        List<Object> list = dataQuery.getResultList();
        for (Object o : list) {
            result.add((T) o);
        }

        return result;
    }

    private <T> List<T> getQueryResult(String sql) {
        Query dataQuery = this.entityManager.createNativeQuery(sql);
        dataQuery.unwrap(SQLQuery.class).setResultTransformer(Transformers.ALIAS_TO_ENTITY_MAP);
        return dataQuery.getResultList();
    }
}
