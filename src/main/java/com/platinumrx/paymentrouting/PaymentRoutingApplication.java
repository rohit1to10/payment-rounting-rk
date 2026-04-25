package com.platinumrx.paymentrouting;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

import com.platinumrx.paymentrouting.config.GatewayConfig;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(GatewayConfig.class)
public class PaymentRoutingApplication {
    public static void main(String[] args) {
        SpringApplication.run(PaymentRoutingApplication.class, args);
    }
}
