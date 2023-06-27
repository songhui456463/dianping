package com.sh.config;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

public class WindowsCondition implements Condition {


    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        //获得当前系统名
        String property = context.getEnvironment().getProperty("os.name");
        //包含Windows则说明是windows系统，返回true
        return property != null && property.contains("Windows");
    }
}