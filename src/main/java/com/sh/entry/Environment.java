package com.sh.entry;

public class Environment {

    private String name;

    public Environment(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return "System{" +
                "name='" + name + '\'' +
                '}';
    }
}
