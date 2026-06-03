package com.diy.framework.context;

import com.diy.framework.beans.factory.*;
import com.diy.framework.context.annotation.*;
import com.diy.framework.context.support.ApplicationObjectSupport;
import com.diy.framework.web.method.HandlerMethod;
import com.diy.framework.web.method.RequestMappingInfo;
import com.diy.framework.web.method.RequestMethodsRequestCondition;
import com.diy.framework.web.mvc.annotation.RequestMapping;
import com.diy.framework.web.server.TomcatWebServer;
import com.diy.framework.web.server.WebServer;
import com.diy.framework.web.servlet.ServletContextInitializer;

import javax.servlet.ServletContext;
import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;

public class ApplicationContext implements BeanFactory {

    public static String APPLICATION_CONTEXT_ATTRIBUTE = ApplicationContext.class.getName();

    private final List<String> beanDefinitionNames = new ArrayList<>(256);
    private final String basePackage;
    private final List<BeanDefinition> beanDefinitionRegistry = new ArrayList<>();
    private final Map<String, Object> beans = new HashMap<>();
    private final Map<Class<?>, Set<String>> allBeanNamesByType = new LinkedHashMap<>();

    public ApplicationContext(String basePackage) {
        this.basePackage = basePackage;
    }

    @Override
    public Object getBean(final String name) {
        return this.beans.get(name);
    }

    @Override
    public <T> T getBean(final Class<T> requiredType) {
        final Set<String> beanNames = this.allBeanNamesByType.get(requiredType);

        if (beanNames == null) {
            throw new RuntimeException("Bean not found '" + requiredType + "'");
        } else if (beanNames.size() != 1) {
            throw new RuntimeException("No qualifying bean of type '" + requiredType
                    + "' available: expected single matching bean but found " + beanNames.size() + ": " + String.join(", ", beanNames));
        }

        final String beanName = beanNames.stream().findFirst().get();

        return (T) this.beans.get(beanName);
    }

    @Override
    public <T> Map<String, T> getBeansOfType(final Class<T> type) {
        final String[] beanNames = getBeanNamesForType(type);
        final LinkedHashMap<String, T> result = new LinkedHashMap<>(beanNames.length);

        Arrays.stream(beanNames)
                .forEach(beanName -> result.put(beanName, (T) getBean(beanName)));

        return result;
    }

    @Override
    public <T> String[] getBeanNamesForType(final Class<T> type) {
        final Set<String> beanNames = this.allBeanNamesByType.get(type);

        if (beanNames == null) {
            throw new RuntimeException("Bean names not found '" + type.getName() + "'");
        }

        return beanNames.toArray(String[]::new);
    }

    @Override
    public String[] getBeanNamesForAnnotation(final Class<? extends Annotation> annotationType) {
        List<String> result = new ArrayList<>();

        for (String beanName : this.beanDefinitionNames) {
            final Object bean = getBean(beanName);
            if (bean != null && findAnnotationOnBean(bean, annotationType) != null) {
                result.add(beanName);
            }
        }

        return result.toArray(new String[0]);
    }

    @Override
    public <A extends Annotation> A findAnnotationOnBean(final Object bean, final Class<A> annotationType) {
        final Set<Class<?>> classes = mapToSuperTypes(bean.getClass());
        for (Class<?> clazz : classes) {
            if (clazz.isAnnotationPresent(annotationType)) {
                return clazz.getAnnotation(annotationType);
            }
        }

        return null;
    }


    public void initialize() {
        BeanScanner beanScanner = new BeanScanner(this.basePackage, "com.diy.framework");
        beanScanner.scanClassesTypeAnnotatedWith(Component.class).forEach(this::registerBean);
        createBeans();

        initApplicationObjectSupport();

        createWebServer();
    }

    private void registerBean(final Class<?> beanClass) {
        this.beanDefinitionRegistry.add(new AnnotatedGenericBeanDefinition(beanClass));
        postProcessBeanDefinitionRegistry(beanClass);
    }

    private void createBeans() {
        beanDefinitionRegistry.forEach(beanDefinition -> {
            final String beanName = beanDefinition.getBeanName();

            if (isBeanInitialized(beanName)) {
                return;
            }

            createInstance(beanDefinition);
        });
    }

    private void postProcessBeanDefinitionRegistry(Class<?> beanClass) {
        Arrays.stream(beanClass.getDeclaredMethods()).filter(method -> method.isAnnotationPresent(Bean.class))
                .forEach(method -> beanDefinitionRegistry.add(new ConfigurationClassBeanDefinition(method, beanClass.getSimpleName())));
    }

    private Object createInstance(BeanDefinition beanDefinition) {
        Executable factoryMethod = beanDefinition.getFactoryMethod();

        try{
            factoryMethod.setAccessible(true);

            Object[] arguments = resolveBeanArguments(beanDefinition.getArgumentsType());

            if(beanDefinition.getFactoryBeanName() == null) {
                Object bean = autowireConstructor((Constructor<?>) factoryMethod, arguments);
                saveBean(beanDefinition.getBeanName(), bean);

                return bean;
            }

            Object bean = instantiateUsingFactoryMethod(beanDefinition, arguments);
            saveBean(beanDefinition.getBeanName(), bean);

            return bean;
        } catch (InvocationTargetException | InstantiationException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }   finally {
            factoryMethod.setAccessible(false);
        }
    }

    private Object[] resolveBeanArguments(List<Class<?>> argumentsType) {
        return argumentsType.stream()
                .map(argumentType -> beanDefinitionRegistry.stream()
                        .filter(definition -> definition.getBeanClass().equals(argumentType))
                        .findFirst()
                        .get())
                .map(beanDefinition -> {
                    String beanName = beanDefinition.getBeanName();
                    if(isBeanInitialized(beanName)) {
                        return getBean(beanName);
                    }

                    return createInstance(beanDefinition);
                }).toArray();
    }

    private Object instantiateUsingFactoryMethod(BeanDefinition beanDefinition, Object[] arguments) throws InvocationTargetException, IllegalAccessException {
        if(!(beanDefinition instanceof ConfigurationClassBeanDefinition)) {
            throw new RuntimeException("required ConfigurationClassBeanDefinition.");
        }

        Method method = (Method) beanDefinition.getFactoryMethod();
        return method.invoke(getFactoryBean(beanDefinition), arguments);
    }

    private Object getFactoryBean(BeanDefinition beanDefinition) {
        String factoryBeanName = beanDefinition.getFactoryBeanName();

        if(isBeanInitialized(factoryBeanName)) {
            return getBean(factoryBeanName);
        }

        BeanDefinition factoryBeanDefinition = beanDefinitionRegistry.stream()
                .filter(definition -> definition.getBeanName().equals(factoryBeanName))
                .findFirst().get();

        return createInstance(factoryBeanDefinition);
    }

    private Object autowireConstructor(Constructor<?> constructor, Object[] arguments) throws InvocationTargetException, InstantiationException, IllegalAccessException {
        return constructor.newInstance(arguments);
    }

    private boolean isBeanInitialized(String beanName) {
        return beans.containsKey(beanName);
    }

    private void saveBean(String beanName, Object bean) {
        if(beans.containsKey(beanName)) {
            throw new RuntimeException("동일한 이름의 빈이 이미 존재합니다.");
        }

        beans.put(beanName, bean);
        beanDefinitionNames.add(beanName);

        mapToSuperTypes(bean.getClass())
                .forEach(clazz -> allBeanNamesByType.computeIfAbsent(clazz, beanType -> new HashSet<>())
                        .add(beanName));
    }

    private Set<Class<?>> mapToSuperTypes(final Class<?> clazz) {
        final HashSet<Class<?>> superTypes = new HashSet<>();
        Class<?> superClass = clazz;

        while (superClass != null) {
            final Class<?>[] interfaces = superClass.getInterfaces();
            superTypes.add(superClass);
            superTypes.addAll(List.of(interfaces));

            superClass = superClass.getSuperclass();
        }

        return superTypes;
    }

    private void initApplicationObjectSupport() {
        final Map<String, ApplicationObjectSupport> supports = getBeansOfType(ApplicationObjectSupport.class);
        supports.values().forEach(support -> support.setApplicationContext(this));
    }

    private void createWebServer() {
        final WebServer webServer = new TomcatWebServer(getSelfInitializer());
        webServer.start();
    }

    private ServletContextInitializer getSelfInitializer() {
        return this::selfInitialize;
    }

    private void selfInitialize(final ServletContext servletContext) {
        prepareWebApplicationContext(servletContext);
    }

    private void prepareWebApplicationContext(final ServletContext servletContext) {
        servletContext.setAttribute(ApplicationContext.APPLICATION_CONTEXT_ATTRIBUTE, this);
    }
}
