package com.cb.auditagent.aspect;

import org.springframework.aop.framework.autoproxy.DefaultAdvisorAutoProxyCreator;
import org.springframework.aop.support.RegexpMethodPointcutAdvisor;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Role;

@Configuration
@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
public class AopLoggingConfig {

    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    public static DefaultAdvisorAutoProxyCreator defaultAdvisorAutoProxyCreator() {
        DefaultAdvisorAutoProxyCreator creator = new DefaultAdvisorAutoProxyCreator();
        creator.setProxyTargetClass(true); // Forces CGLIB proxying on class-based beans
        return creator;
    }

    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    public static RegexpMethodPointcutAdvisor loggingAdvisor() {
        RegexpMethodPointcutAdvisor advisor = new RegexpMethodPointcutAdvisor();
        advisor.setPatterns(
                "com\\.cb\\.auditagent\\.service\\..*",
                "com\\.cb\\.auditagent\\.controller\\..*");
        advisor.setAdvice(new CustomLoggingInterceptor());
        return advisor;
    }
}
