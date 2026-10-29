package cn.labmanage;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@MapperScan("cn.labmanage")
@EnableScheduling
public class LabManageApplication {
    public static void main(String[] args) { SpringApplication.run(LabManageApplication.class, args); }
}
