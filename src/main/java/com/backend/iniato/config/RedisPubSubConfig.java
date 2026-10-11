package com.backend.iniato.config;

import com.backend.iniato.dto.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;

import java.util.concurrent.CountDownLatch;

@Configuration
public class RedisPubSubConfig {

    private static final String ROUTE_EVENTS_CHANNEL = "route:events";
    private static final String DRIVER_EVENTS_CHANNEL = "driver:events";
    private static final String RIDE_EVENTS_CHANNEL = "ride:events";

    @Bean
    @Lazy
    @ConditionalOnProperty(name = "spring.redis.enabled", havingValue = "true", matchIfMissing = true)
    public RedisMessageListenerContainer redisMessageListenerContainer(
            RedisConnectionFactory connectionFactory,
            RouteEventMessageListener routeEventListener,
            DriverEventMessageListener driverEventListener,
            RideEventMessageListener rideEventListener) {

        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);

        container.addMessageListener(new MessageListenerAdapter(routeEventListener, "onMessage"), new PatternTopic(ROUTE_EVENTS_CHANNEL));
        container.addMessageListener(new MessageListenerAdapter(driverEventListener, "onMessage"), new PatternTopic(DRIVER_EVENTS_CHANNEL));
        container.addMessageListener(new MessageListenerAdapter(rideEventListener, "onMessage"), new PatternTopic(RIDE_EVENTS_CHANNEL));

        return container;
    }

    @Bean
    @Lazy
    public RouteEventMessageListener routeEventListener() {
        return new RouteEventMessageListener();
    }

    @Bean
    @Lazy
    public DriverEventMessageListener driverEventListener() {
        return new DriverEventMessageListener();
    }

    @Bean
    @Lazy
    public RideEventMessageListener rideEventListener() {
        return new RideEventMessageListener();
    }

    public static class RouteEventMessageListener {
        private final CountDownLatch latch = new CountDownLatch(1);

        public void onMessage(String message) {
            System.out.println("Received Route Event: " + message);
        }
    }

    public static class DriverEventMessageListener {
        private final CountDownLatch latch = new CountDownLatch(1);

        public void onMessage(String message) {
            System.out.println("Received Driver Event: " + message);
        }
    }

    public static class RideEventMessageListener {
        private final CountDownLatch latch = new CountDownLatch(1);

        public void onMessage(String message) {
            System.out.println("Received Ride Event: " + message);
        }
    }
}