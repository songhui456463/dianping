package com.sh;

import org.junit.jupiter.api.Test;

public class ThreadLocalTest {

    private static final ThreadLocal<Person> THREAD_LOCAL = new InheritableThreadLocal<>();

    @Test
    public void fun2() throws InterruptedException {
        setData(new Person());

        Thread subThread1 = new Thread(() -> {
            Person data = getAndPrintData();
            if (data != null)
                data.setAge(100);
            getAndPrintData(); // 再打印一次
        });
        subThread1.start();
        subThread1.join();


        Thread subThread2 = new Thread(() -> getAndPrintData());
        subThread2.start();
        subThread2.join();

        // 主线程获取线程绑定内容
        getAndPrintData();
        System.out.println("======== Finish =========");
    }


    private void setData(Person person) {
        System.out.println("set数据，线程名：" + Thread.currentThread().getName());
        THREAD_LOCAL.set(person);
    }

    private Person getAndPrintData() {
        // 拿到当前线程绑定的一个变量，然后做逻辑（本处只打印）
        Person person = THREAD_LOCAL.get();
        System.out.println("get数据，线程名：" + Thread.currentThread().getName() + "，数据为：" + person);
        return person;
    }

}
