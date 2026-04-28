package com.diy.framework.web.render.resolver;

public class ViewResolverFactory {
    private static final ViewResolver jspViewResolver = new JspViewResolver();
    private static final ViewResolver redirectViewResolver = new RedirectViewResolver();

    public static ViewResolver getViewResolver(String viewName) {
        if(viewName.startsWith("redirect:")) {
            return redirectViewResolver;
        } else {
            return jspViewResolver;
        }
    }
}
