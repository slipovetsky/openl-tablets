package org.openl.rules.ruleservice.publish;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.openl.rules.ruleservice.management.ServiceManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

@RunWith(SpringJUnit4ClassRunner.class)
@TestPropertySource(properties = { "ruleservice.isProvideRuntimeContext=false",
        "ruleservice.rmiPort=31099",
        "production-repository.uri=test-resources/StaticRmiHandlerTest",
        "production-repository.factory = repo-file"})
@ContextConfiguration(locations = { "classpath:openl-ruleservice-beans.xml" })
public class StaticRmiHandlerTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    public void test() throws Exception {
        Assert.assertNotNull(applicationContext);
        ServiceManager serviceManager = applicationContext.getBean("serviceManager", ServiceManager.class);
        Assert.assertNotNull(serviceManager);

        Registry registry = LocateRegistry.getRegistry(31099);
        StaticRmiHandler staticRmiHandler = (StaticRmiHandler) registry.lookup("staticRmiHandler");

        Assert.assertNotNull(staticRmiHandler);

        String result = staticRmiHandler.baseHello(10);

        Assert.assertEquals("Good Morning", result);

    }
}
