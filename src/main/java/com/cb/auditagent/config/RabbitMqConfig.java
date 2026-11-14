package com.cb.auditagent.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


/**
 * RabbitMQ topology and messaging configuration.
 * Declares durable direct exchanges, task queues with prefetch and priority support,
 * dead-letter exchanges for resilient failure recovery, and JSON serialization.
 */
@Configuration
@ConditionalOnClass(RabbitTemplate.class)
public class RabbitMqConfig {

    public static final String EXCHANGE_NAME = "auditagent.direct";
    public static final String SCAN_QUEUE = "auditagent.scan.queue";
    public static final String REMEDIATION_QUEUE = "auditagent.remediation.queue";
    public static final String DLX_EXCHANGE = "auditagent.dlx";
    public static final String DLQ_NAME = "auditagent.dlq";
    public static final String EVENTS_EXCHANGE = "auditagent.events";

    public static final String ROUTING_KEY_SCAN = "task.scan";
    public static final String ROUTING_KEY_REMEDIATION = "task.remediation";

    @Bean
    public DirectExchange auditagentExchange() {
        return new DirectExchange(EXCHANGE_NAME, true, false);
    }

    @Bean
    public FanoutExchange deadLetterExchange() {
        return new FanoutExchange(DLX_EXCHANGE, true, false);
    }

    @Bean
    public FanoutExchange eventsExchange() {
        return new FanoutExchange(EVENTS_EXCHANGE, true, false);
    }

    @Bean
    public Queue eventIngressQueue() {
        return new AnonymousQueue();
    }

    @Bean
    public Binding eventIngressBinding(Queue eventIngressQueue, FanoutExchange eventsExchange) {
        return BindingBuilder.bind(eventIngressQueue).to(eventsExchange);
    }

    @Bean
    public Queue deadLetterQueue() {
        return QueueBuilder.durable(DLQ_NAME).build();
    }

    @Bean
    public Binding deadLetterBinding(Queue deadLetterQueue, FanoutExchange deadLetterExchange) {
        return BindingBuilder.bind(deadLetterQueue).to(deadLetterExchange);
    }

    @Bean
    public Queue scanQueue() {
        return QueueBuilder.durable(SCAN_QUEUE)
                .withArgument("x-dead-letter-exchange", DLX_EXCHANGE)
                .withArgument("x-max-priority", 10)
                .build();
    }

    @Bean
    public Binding scanBinding(Queue scanQueue, DirectExchange auditagentExchange) {
        return BindingBuilder.bind(scanQueue).to(auditagentExchange).with(ROUTING_KEY_SCAN);
    }

    @Bean
    public Queue remediationQueue() {
        return QueueBuilder.durable(REMEDIATION_QUEUE)
                .withArgument("x-dead-letter-exchange", DLX_EXCHANGE)
                .withArgument("x-max-priority", 10)
                .build();
    }

    @Bean
    public Binding remediationBinding(Queue remediationQueue, DirectExchange auditagentExchange) {
        return BindingBuilder.bind(remediationQueue).to(auditagentExchange).with(ROUTING_KEY_REMEDIATION);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            MessageConverter jsonMessageConverter,
            @Value("${spring.rabbitmq.listener.simple.auto-startup:true}") boolean autoStartup) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(jsonMessageConverter);
        factory.setAutoStartup(autoStartup);
        return factory;
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter jsonMessageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter);
        return template;
    }
}
