package com.testhub.uiexec.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.testhub.uiexec.domain.ExecutorType;
import com.testhub.uiexec.domain.FailureCategory;
import com.testhub.uiexec.domain.TaskEntity;
import com.testhub.uiexec.domain.TaskStatus;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.EmbeddedKafkaKraftBroker;
import org.springframework.kafka.test.utils.KafkaTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the Kafka adapter against a real in-process Kafka broker (KRaft).
 * The runtime acceptance run additionally verified the same adapter against the
 * standalone broker in deploy/docker-compose.infra.yml.
 */
class KafkaTaskEventPublisherTest {

    private static EmbeddedKafkaBroker broker;

    @BeforeAll
    static void startBroker() {
        broker = new EmbeddedKafkaKraftBroker(1, 1,
                KafkaTaskEventPublisher.DISPATCH_TOPIC, KafkaTaskEventPublisher.RESULT_TOPIC);
        broker.afterPropertiesSet();
    }

    @AfterAll
    static void stopBroker() {
        if (broker != null) {
            broker.destroy();
        }
    }

    @Test
    void publishesDispatchAndResultEvents() throws Exception {
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        Map<String, Object> producerProps = KafkaTestUtils.producerProps(broker);
        producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        KafkaTemplate<String, String> template = new KafkaTemplate<>(
                new DefaultKafkaProducerFactory<>(producerProps));
        KafkaTaskEventPublisher publisher = new KafkaTaskEventPublisher(template, mapper);

        TaskEntity task = new TaskEntity("task-kafka-1", "case-1", ExecutorType.AI_RUNTIME,
                TaskStatus.FAILED, 120, 30, Instant.now());
        task.setStatus(TaskStatus.FAILED);
        task.setFailureReason(FailureCategory.MODEL_DECISION_FAILED);
        task.setFailureSummary("模型输出无法解析");
        task.setFailureReportUrl("http://example/report.json");

        Map<String, Object> consumerProps = KafkaTestUtils.consumerProps("testhub-kafka-test", "false", broker);
        try (Consumer<String, String> consumer = new org.apache.kafka.clients.consumer.KafkaConsumer<>(
                consumerProps, new StringDeserializer(), new StringDeserializer())) {
            broker.consumeFromAnEmbeddedTopic(consumer, KafkaTaskEventPublisher.RESULT_TOPIC);
            publisher.finished(task, null);

            ConsumerRecord<String, String> record = KafkaTestUtils.getSingleRecord(
                    consumer, KafkaTaskEventPublisher.RESULT_TOPIC, Duration.ofSeconds(15));
            assertThat(record.key()).isEqualTo("task-kafka-1");
            TaskEvent event = mapper.readValue(record.value(), TaskEvent.class);
            assertThat(event.type()).isEqualTo("TASK_FINISHED");
            assertThat(event.taskId()).isEqualTo("task-kafka-1");
            assertThat(event.status()).isEqualTo("FAILED");
            assertThat(event.failureReason()).isEqualTo("MODEL_DECISION_FAILED");
            assertThat(event.failureReportUrl()).isEqualTo("http://example/report.json");
        }
    }
}
