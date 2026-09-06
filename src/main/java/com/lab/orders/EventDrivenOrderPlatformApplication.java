package com.lab.orders;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import com.lab.orders.config.LabProperties;

@SpringBootApplication
@EnableConfigurationProperties(LabProperties.class)
public class EventDrivenOrderPlatformApplication {

	public static void main(String[] args) {
		SpringApplication.run(EventDrivenOrderPlatformApplication.class, args);
	}
}
