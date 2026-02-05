package org.linkki.inspector;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.grid.Grid;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.After;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.linkki.core.binding.descriptor.BindingDescriptor;
import org.linkki.core.binding.wrapper.ComponentWrapper;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Deque;

/**
 * Enhanced AspectJ interceptor with strict PMO selection and Container support.
 */
@Aspect
@org.springframework.stereotype.Component
public class LinkkiBindingInterceptor {

    // Track the current PMO context stack for nested PMOs
    private static final ThreadLocal<Deque<Object>> pmoContextStack =
            ThreadLocal.withInitial(java.util.ArrayDeque::new);

    LinkkiBindingInterceptor() {
        System.out.println("Enhanced LinkkiBindingInterceptor initialized!");
    }

    // ========== POINTCUTS ==========

    @Pointcut("execution(* org.linkki.core.binding.BindingContext.bind*(..))")
    public void bindingContextBind() {
    }

    @Pointcut("execution(* org.linkki.core.ui.creation.section.PmoBasedSectionFactory.createSection(..))")
    public void sectionFactoryCreate() {
    }

    @Pointcut("execution((*..*..*Pmo+).new(..))")
    public void pmoConstructorCreate() {
    }

    @Pointcut("execution(com.vaadin.flow.component.Component+.new(..))")
    public void vaadinComponentConstructorCreate() {
    }

    // ========== ADVICE ==========

    @After("pmoConstructorCreate()")
    public void pmoConstructorCreateNew(JoinPoint joinPoint) {
        Object result = joinPoint.getThis();
        if (isPmo(result)) {
            ComponentRegistry.registerInstantiationLocation(result, ComponentRegistry.captureInstantiationLocation(result.getClass()));
        }
    }

    /**
     * Intercepts the creation of ALL Vaadin components to register them with the Inspector.
     * This provides a base level of inspection (instantiation location) for everything.
     * If the component is later bound to a PMO (via aroundBind), that registration will overwrite this one.
     */
    @After("vaadinComponentConstructorCreate()")
    public void vaadinComponentConstructor(JoinPoint joinPoint) {
        if (!LinkkiInspectorUIInjector.isEnabled()) {
            return;
        }
        Object result = joinPoint.getThis();
        if (result instanceof Component) {
            ComponentInspector.registerGenericComponent((Component) result);
        }
    }

    @Around("bindingContextBind()")
    public Object aroundBind(ProceedingJoinPoint joinPoint) throws Throwable {
        var result = joinPoint.proceed();

        if (!LinkkiInspectorUIInjector.isEnabled()) {
            return result;
        }

        var pmo = joinPoint.getArgs()[0];
        var bindingDescriptor = (BindingDescriptor) joinPoint.getArgs()[1];
        var componentWrapper = (ComponentWrapper) joinPoint.getArgs()[2];

        registerBinding(pmo, bindingDescriptor, componentWrapper);

        return result;
    }

    @Around("sectionFactoryCreate()")
    public Object aroundSectionCreation(ProceedingJoinPoint joinPoint) throws Throwable {
        var pmo = findPmoInArguments(joinPoint.getArgs());

        try {
            var result = joinPoint.proceed();

            if (!LinkkiInspectorUIInjector.isEnabled()) {
                return result;
            }

            if (result instanceof Component && pmo != null) {
                try {
                    // Sections often represent the PMO itself, effectively the "root" property
                    ComponentInspector.registerPmoComponent(pmo, (Component) result, "");
                } catch (Exception e) {
                    System.err.println("Inspector: Failed to register section - " + e.getMessage());
                }
            }

            return result;
        } finally {
            if (pmo != null && !pmoContextStack.get().isEmpty()) {
                pmoContextStack.get().pop();
            }
        }
    }

    // ========== HELPER METHODS ==========

    private void registerBinding(Object pmo, BindingDescriptor bindingDescriptor, ComponentWrapper componentWrapper) throws Exception {
        var component = (Component) componentWrapper.getComponent();
        var propertyName = bindingDescriptor.getBoundProperty().getPmoProperty();

        if (!ignoreComponent(component)) {
            ComponentInspector.registerPmoComponent(pmo, component, propertyName);
        }
    }

    private boolean ignoreComponent(Component component) {
        return component instanceof Grid.Column;
    }

    private Object findPmoInArguments(Object[] args) {
        for (var arg : args) {
            if (isPmo(arg)) {
                return arg;
            }
        }
        return null;
    }

    /**
     * Robust check to determine if an object is a PMO.
     */
    private boolean isPmo(Object obj) {
        if (obj == null) return false;
        return isPmoClass(obj.getClass());
    }

    private boolean isPmoClass(Class<?> clazz) {
        if (clazz == null || clazz == Object.class) return false;

        if (Arrays.stream(clazz.getInterfaces())
                .anyMatch(i -> i.getName().contains("ContainerPmo"))) {
            return true;
        }

        if (clazz.isAnnotationPresent(org.linkki.core.ui.layout.annotation.UISection.class)) {
            return true;
        }

        for (var method : clazz.getDeclaredMethods()) {
            if (hasLinkkiAnnotation(method)) {
                return true;
            }
        }

        return isPmoClass(clazz.getSuperclass());
    }

    private boolean hasLinkkiAnnotation(Method method) {
        return method.isAnnotationPresent(org.linkki.core.ui.element.annotation.UITextField.class)
                || method.isAnnotationPresent(org.linkki.core.ui.element.annotation.UIButton.class)
                || method.isAnnotationPresent(org.linkki.core.ui.element.annotation.UIComboBox.class)
                || method.isAnnotationPresent(org.linkki.core.ui.element.annotation.UICheckBox.class)
                || method.isAnnotationPresent(org.linkki.core.ui.element.annotation.UIDateField.class)
                || method.isAnnotationPresent(org.linkki.core.ui.element.annotation.UIIntegerField.class)
                || method.isAnnotationPresent(org.linkki.core.ui.element.annotation.UIDoubleField.class)
                || method.isAnnotationPresent(org.linkki.core.ui.element.annotation.UITextArea.class)
                || method.isAnnotationPresent(org.linkki.core.ui.element.annotation.UILabel.class);
    }

    @SuppressWarnings("unchecked")
    private <T> T getFieldValue(Object obj, String fieldName, Class<T> fieldType) {
        try {
            var field = findField(obj.getClass(), fieldName);
            if (field != null) {
                field.setAccessible(true);
                return (T) field.get(obj);
            }
        } catch (Exception e) {
            // Ignore
        }
        return null;
    }

    private Field findField(Class<?> clazz, String fieldName) {
        var current = clazz;
        while (current != null && current != Object.class) {
            try {
                return current.getDeclaredField(fieldName);
            } catch (NoSuchFieldException e) {
                current = current.getSuperclass();
            }
        }
        return null;
    }
}