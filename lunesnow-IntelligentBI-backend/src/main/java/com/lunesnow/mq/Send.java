package com.lunesnow.mq;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

import java.nio.charset.StandardCharsets;

public class Send {

    private final static String QUEUE_NAME = "hello";

    public static void main(String[] argv) throws Exception {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost("localhost");

        try (Connection connection = factory.newConnection();
             Channel channel = connection.createChannel()) {
            /**
             * 声明一个队列
             * 1. 队列名称
             * 2. 是否持久化
             * 3. 是否排他性
             * 4. 是否自动删除
             * 5. 队列参数
             */
            channel.queueDeclare(QUEUE_NAME, true, false, false, null);
            /**
             * 发布一条消息
             * 1. 交换机名称
             * 2. 队列名称
             * 3. 消息属性
             * 4. 消息体
             */
            String message = "Hello RabbitMQ!";
            /**
             * 发布消息到队列
             * 1. 交换机名称
             * 2. 队列名称
             * 3. 消息属性
             * 4. 消息体
             */
            channel.basicPublish("", QUEUE_NAME, null, message.getBytes(StandardCharsets.UTF_8));
            System.out.println(" [x] Sent '" + message + "'");
        }
    }
}