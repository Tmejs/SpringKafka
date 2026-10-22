package com.example.fundorders.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.kafka.autoconfigure.KafkaAutoConfiguration;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.kafka.core.KafkaAdmin;

class KafkaTopicConfigurationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withConfiguration(AutoConfigurations.of(KafkaAutoConfiguration.class))
            .withUserConfiguration(KafkaTopicConfiguration.class)
            .withPropertyValues("spring.kafka.admin.auto-create=false",
                    "spring.kafka.bootstrap-servers=broker.example:19092");

    @Test
    void defaultsSupportSingleBrokerWithMatchingSourceAndDlqPartitions() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertTopic(context.getBean("sourceTopic", NewTopic.class), "fund-order-events", 3, 1);
            assertTopic(context.getBean("deadLetterTopic", NewTopic.class), "fund-order-events.DLQ", 3, 1);
        });
    }

    @Test
    void overridesApplyToBothTopics() {
        runner.withPropertyValues("app.kafka.topic=custom-orders", "app.kafka.dlq-topic=custom-errors",
                "app.kafka.partitions=6", "app.kafka.replication-factor=3").run(context -> {
                    assertThat(context).hasNotFailed();
                    assertTopic(context.getBean("sourceTopic", NewTopic.class), "custom-orders", 6, 3);
                    assertTopic(context.getBean("deadLetterTopic", NewTopic.class), "custom-errors", 6, 3);
                });
    }

    @ParameterizedTest
    @ValueSource(strings = {"app.kafka.topic=", "app.kafka.dlq-topic= ", "app.kafka.partitions=0",
            "app.kafka.replication-factor=0", "app.kafka.replication-factor=32768"})
    void invalidTopicSettingsFailStartup(String property) {
        runner.withPropertyValues(property).run(context -> assertThat(context).hasFailed());
    }

    @Test
    void bootProvidesAdminWithSharedBootstrapConfigurationWhenCreationIsDisabled() {
        runner.run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(KafkaAdmin.class);
            assertThat(context.getBean(KafkaAdmin.class).getConfigurationProperties())
                    .containsEntry(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG,
                            java.util.List.of("broker.example:19092"));
        });
    }

    private static void assertTopic(NewTopic topic, String name, int partitions, int replicas) {
        assertThat(topic.name()).isEqualTo(name);
        assertThat(topic.numPartitions()).isEqualTo(partitions);
        assertThat(topic.replicationFactor()).isEqualTo((short) replicas);
    }
}
