package com.diy.framework.web.render.resolver;

import com.diy.framework.web.render.view.RedirectView;
import com.diy.framework.web.render.view.View;

public class RedirectViewResolver implements ViewResolver {
    @Override
    public View resolveViewName(String viewName) {
        return new RedirectView(viewName);
    }
}
