package com.diy.framework.web.mvc.view;

import com.diy.framework.core.Ordered;

public class JspViewResolver implements ViewResolver, Ordered {
    private int order = 0;

    @Override
    public View resolveViewName(String viewName) {
        return new JspView("/" + viewName + ".jsp");
    }

    @Override
    public int getOrder() {
        return this.order;
    }

    public void setOrder(int order) {
        this.order = order;
    }
}
