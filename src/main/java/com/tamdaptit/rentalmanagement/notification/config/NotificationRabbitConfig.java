package com.tamdaptit.rentalmanagement.notification.config;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class NotificationRabbitConfig {
    public static final String EXCHANGE = "rental.notifications";
    public static final String QUEUE = "rental.notifications.inbox";
    public static final String ROUTING_KEY = "notification.created";
    public static final String DEAD_EXCHANGE = "rental.notifications.dead";
    public static final String DEAD_QUEUE = "rental.notifications.dead.inbox";

    @Bean
    public Declarables notificationTopology() {
        var exchange = new DirectExchange(EXCHANGE, true, false);
        var deadExchange = new DirectExchange(DEAD_EXCHANGE, true, false);
        var queue = QueueBuilder.durable(QUEUE).deadLetterExchange(DEAD_EXCHANGE)
                .deadLetterRoutingKey(ROUTING_KEY).build();
        var deadQueue = QueueBuilder.durable(DEAD_QUEUE).build();
        return new Declarables(exchange, deadExchange, queue, deadQueue,
                BindingBuilder.bind(queue).to(exchange).with(ROUTING_KEY),
                BindingBuilder.bind(deadQueue).to(deadExchange).with(ROUTING_KEY));
    }
}
