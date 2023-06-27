package com.sh.utils;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.atomic.AtomicInteger;

@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface VisitCount {

    String name();

}
