package org.linkki.inspector;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.EnableLoadTimeWeaving;

/**
 * Configuration class for Spring-based applications
 */
@Configuration
@EnableAspectJAutoProxy(proxyTargetClass = true)
@EnableLoadTimeWeaving(aspectjWeaving = EnableLoadTimeWeaving.AspectJWeaving.ENABLED)
public class LinkkiInspectorConfiguration {

    @Bean
    public LinkkiBindingInterceptor linkkiBindingInterceptor() {
        return new LinkkiBindingInterceptor();
    }
}