package com.learn.aiintelligenttourism.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
public class KafkaConfig {

    public static final String TOPIC_TEXT_TURN = "memory-text-turn-topic";
    public static final String TOPIC_ITINERARY = "memory-itinerary-topic";

    @Bean
    public NewTopic textTurnTopic() {
        return TopicBuilder.name(TOPIC_TEXT_TURN)
                .partitions(6) // 【算力扩容】划分为 6 个并行拉取分区，与 application.yml 并发度匹配
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic itineraryTopic() {
        return TopicBuilder.name(TOPIC_ITINERARY)
                .partitions(6)
                .replicas(1)
                .build();
    }

    /**
     * 【简历亮点：DLQ (Dead Letter Queue) 与失败重发保障】
     * 解决消费大模型因为限流、数据库宕机时的兜底：
     * 方案：如果报错抛出异常，将间隔 2 秒重试；重试 3 次仍然抛错，
     * 将其平移抛往 .DLT 结尾的死信死信队列中（等待查明后补偿），保证大盘不受阻！
     */
    @Bean
    public DefaultErrorHandler errorHandler(KafkaTemplate<Object, Object> template) {
        FixedBackOff backOff = new FixedBackOff(2000L, 3);
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(template);
        return new DefaultErrorHandler(recoverer, backOff);
    }
}
