package com.sh;

import com.sh.entry.MyField;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.lang.reflect.Field;

public class MyFieldTest {

    @MyField(description = "用户名", length = 12)
    private String userName;

    @Test
    void testMyField() {
        // 获取类模板
        Class<MyFieldTest> c = MyFieldTest.class;

        // 获取所有字段
        for (Field f : c.getDeclaredFields()) {
            // 判断这个字段是否有MyField注解
            if(f.isAnnotationPresent(MyField.class)){
                MyField annotation = f.getAnnotation(MyField.class);
                System.out.println("字段:[" + f.getName() + "], 描述:[" + annotation.description() + "], 长度:[" + annotation.length() +"]");
            }
        }
    }
}
