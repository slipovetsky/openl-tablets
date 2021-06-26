package org.openl.rules.ruleservice.conf;

import org.openl.rules.ruleservice.management.ServiceManager;
import org.openl.spring.env.DynamicPropertySource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.scheduling.annotation.Scheduled;

public class SchedulingTasks implements ApplicationContextAware {
    private final Logger log = LoggerFactory.getLogger(SchedulingTasks.class);

    private ServiceManager serviceManager;
    private ApplicationContext applicationContext;

    public ApplicationContext getApplicationContext() {
        return applicationContext;
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    public ServiceManager getServiceManager() {
        return serviceManager;
    }

    public void setServiceManager(ServiceManager serviceManager) {
        this.serviceManager = serviceManager;
    }

    @Scheduled(fixedDelay = 5000)
    public void updateSpringDynamicProperties() {
        if (DynamicPropertySource.get().reloadIfModified()) {
            log.info("Dynamic properties are changed. All deployed services are going to be redeployed...");
            try {
                ((ClassPathXmlApplicationContext) applicationContext).refresh();
                serviceManager.redeployAll();
            } finally {
                log.info("Services redeployment is complete.");
            }
        }
    }
}
