package com.diy.app;

import com.diy.framework.context.ApplicationContext;
import com.diy.framework.web.server.TomcatWebServer;

public class LectureApplication {
    public static void main(String[] args) {
        ApplicationContext applicationContext = new ApplicationContext(LectureApplication.class.getPackageName());
        applicationContext.initialize();

        TomcatWebServer tomcatWebServer = new TomcatWebServer();
        tomcatWebServer.start();
    }
}
