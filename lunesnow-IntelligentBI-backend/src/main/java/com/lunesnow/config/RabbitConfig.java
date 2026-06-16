package com.lunesnow.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

/**
 * RabbitMQ 配置
 * 配置交换机、队列、绑定关系、死信队列
 */
@Configuration
public class RabbitConfig {

    // ==================== 主队列配置 ====================

    /**
     * 交换机名称
     */
    public static final String CHART_EXCHANGE = "chart.exchange";

    /**
     * 队列名称
     */
    public static final String CHART_QUEUE = "chart.queue";

    /**
     * 路由键
     */
    public static final String CHART_ROUTING_KEY = "chart.generate";

    // ==================== 死信队列配置 ====================

    /**
     * 死信交换机名称
     */
    public static final String DEAD_LETTER_EXCHANGE = "chart.dead-letter.exchange";

    /**
     * 死信队列名称
     */
    public static final String DEAD_LETTER_QUEUE = "chart.dead-letter.queue";

    /**
     * 死信路由键
     */
    public static final String DEAD_LETTER_ROUTING_KEY = "chart.dead-letter";

    // ==================== 主队列 ====================

    /**
     * 创建主交换机
     */
    @Bean
    public DirectExchange chartExchange() {
        return new DirectExchange(CHART_EXCHANGE, true, false);
    }

    /**
     * 创建主队列（绑定死信交换机）
     * 消费失败时，消息会发送到死信交换机
     */
    @Bean
    public Queue chartQueue() {
        Map<String, Object> args = new HashMap<>();
        args.put("x-dead-letter-exchange", DEAD_LETTER_EXCHANGE);
        args.put("x-dead-letter-routing-key", DEAD_LETTER_ROUTING_KEY);
        args.put("x-message-ttl", 60000); // 消息 TTL：60 秒（可选）
        return QueueBuilder.durable(CHART_QUEUE).withArguments(args).build();
    }

    /**
     * 绑定主队列到主交换机
     */
    @Bean
    public Binding chartBinding(Queue chartQueue, DirectExchange chartExchange) {
        return BindingBuilder.bind(chartQueue).to(chartExchange).with(CHART_ROUTING_KEY);
    }

    // ==================== 死信队列 ====================

    /**
     * 创建死信交换机
     */
    @Bean
    public DirectExchange deadLetterExchange() {
        return new DirectExchange(DEAD_LETTER_EXCHANGE, true, false);
    }

    /**
     * 创建死信队列
     * 存储处理失败的消息
     * 消息 TTL：24 小时后自动删除
     */
    @Bean
    public Queue deadLetterQueue() {
        Map<String, Object> args = new HashMap<>();
        args.put("x-message-ttl", 24 * 60 * 60 * 1000); // 24 小时
        return QueueBuilder.durable(DEAD_LETTER_QUEUE).withArguments(args).build();
    }

    /**
     * 绑定死信队列到死信交换机
     */
    @Bean
    public Binding deadLetterBinding(Queue deadLetterQueue, DirectExchange deadLetterExchange) {
        return BindingBuilder.bind(deadLetterQueue).to(deadLetterExchange).with(DEAD_LETTER_ROUTING_KEY);
    }

    // ==================== 消息转换器 ====================

    /**
     * 使用 JSON 消息转换器（替代默认的 JDK 序列化）
     */
    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
