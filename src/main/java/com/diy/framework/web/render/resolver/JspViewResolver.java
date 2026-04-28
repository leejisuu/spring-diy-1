package com.diy.framework.web.render.resolver;

import com.diy.framework.web.render.view.JspView;
import com.diy.framework.web.render.view.View;

public class JspViewResolver implements ViewResolver {
    @Override
    public View resolveViewName(String viewName) {
        return new JspView(viewName);
    }
}
