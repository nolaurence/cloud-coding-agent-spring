package com.cca.spring;

import io.mybatis.mapper.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@ConfigurationPropertiesScan
@MapperScan(basePackages = "com.cca.spring.mapper", markerInterface = Mapper.class)
public class CloudCodingAgentApplication {

  public static void main(String[] args) {
    SpringApplication.run(CloudCodingAgentApplication.class, args);
  }
}
