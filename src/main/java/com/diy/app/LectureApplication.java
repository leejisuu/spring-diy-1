package com.diy.app;

import com.diy.framework.context.ApplicationContext;

public class LectureApplication {
    public static void main(String[] args) {
        ApplicationContext applicationContext = new ApplicationContext(LectureApplication.class.getPackageName());
        applicationContext.initialize();
    }
}
