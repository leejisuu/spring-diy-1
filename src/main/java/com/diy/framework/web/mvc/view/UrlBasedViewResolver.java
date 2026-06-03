package com.diy.framework.web.mvc.view;

import com.diy.framework.core.Ordered;

public class UrlBasedViewResolver implements ViewResolver, Ordered {
    private final String REDIRECT_PREFIX = "redirect:";
    private int order = 0;

    @Override
    public View resolveViewName(String viewName) {
        if(viewName.startsWith(REDIRECT_PREFIX)) {
            return new RedirectView(viewName.substring(REDIRECT_PREFIX.length()));
        }

        return null;
    }

    @Override
    public int getOrder() {
        return this.order;
    }

    public void setOrder(int order) {
        this.order = order;
    }
}
