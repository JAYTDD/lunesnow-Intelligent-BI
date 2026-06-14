package com.lunesnow.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 配置
 * 配置交换机、队列、绑定关系
 */
@Configuration
public class RabbitConfig {

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

    /**
     * 创建交换机
     */
    @Bean
    public DirectExchange chartExchange() {
        return new DirectExchange(CHART_EXCHANGE, true, false);
    }

    /**
     * 创建队列（durable=true 表示持久化，重启后不丢失）
     */
    @Bean
    public Queue chartQueue() {
        return new Queue(CHART_QUEUE, true);
    }

    /**
     * 绑定队列到交换机
     */
    @Bean
    public Binding chartBinding(Queue chartQueue, DirectExchange chartExchange) {
        return BindingBuilder.bind(chartQueue).to(chartExchange).with(CHART_ROUTING_KEY);
    }

    /**
     * 使用 JSON 消息转换器（替代默认的 JDK 序列化）
     */
    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
