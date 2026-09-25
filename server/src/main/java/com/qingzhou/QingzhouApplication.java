package com.qingzhou;

import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan(basePackages = "com.qingzhou.modules", annotationClass = Mapper.class)
public class QingzhouApplication {

    public static void main(String[] args) {
        SpringApplication.run(QingzhouApplication.class, args);
    }
}
