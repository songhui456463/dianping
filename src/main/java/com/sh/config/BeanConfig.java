package com.sh.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import com.sh.entry.Environment;

@Configuration
public class BeanConfig {

    @Conditional(WindowsCondition.class)
    @Bean(name = "windows")
    public Environment system1() {
        return new Environment("Windows");
    }

    @Conditional(LinuxCondition.class)
    @Bean(name = "linux")
    public Environment system2() {
        return new Environment("Linux");
    }
}
