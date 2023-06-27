package com.sh.dto;


public class UserHolder {

    private static final ThreadLocal<UserDTO> t1 = new ThreadLocal<>();

    public static void saveUser(UserDTO user) {
        t1.set(user);
    }

    public static UserDTO getUser() {
        return t1.get();
    }

    public static void removeUser(){
        System.out.println("销毁UserDTO");
        t1.remove();
    }
}
