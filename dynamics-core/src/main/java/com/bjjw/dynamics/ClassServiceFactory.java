package com.bjjw.dynamics;

public class ClassServiceFactory {

    private static volatile ClassService instance;

    public static ClassService getInstance() {
        if (instance == null) {
            synchronized (ClassServiceFactory.class) {
                if (instance == null) {
                    instance = new JvmAttachClassServiceImpl();
                }
            }
        }
        return instance;
    }
}