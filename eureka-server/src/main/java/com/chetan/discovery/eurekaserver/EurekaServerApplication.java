package com.chetan.discovery.eurekaserver;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.server.EnableEurekaServer;
@EnableEurekaServer
/** Starts the eureka-server application. */
@SpringBootApplication
public class EurekaServerApplication {
 public static void main(String[] args) { SpringApplication.run(EurekaServerApplication.class, args); }
}
