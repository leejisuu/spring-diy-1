package com.diy.framework.web.render.resolver;

import com.diy.framework.web.render.view.View;

public interface ViewResolver {
    View resolveViewName(String viewName);
}
