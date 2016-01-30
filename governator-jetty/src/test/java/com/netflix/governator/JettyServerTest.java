package com.netflix.governator;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

import javax.annotation.PreDestroy;

import junit.framework.Assert;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.mockito.Mockito;
import org.testng.annotations.Test;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.util.Modules;
import com.netflix.governator.guice.jetty.DefaultJettyConfig;
import com.netflix.governator.guice.jetty.JettyConfig;
import com.netflix.governator.guice.jetty.JettyModule;

public class JettyServerTest {
    public static class Foo {
        @PreDestroy
        void shutdown() {
        }
    }
    
    @Test
    public void confirmShutdownSequence() throws InterruptedException, MalformedURLException, IOException {
        final Foo foo = Mockito.mock(Foo.class);
        
        // Create the injector and autostart Jetty
        LifecycleInjector injector = InjectorBuilder.fromModules(
                new SampleServletModule(), 
                new ShutdownHookModule(), 
                Modules.override(new JettyModule())
                       .with(new AbstractModule() {
                            @Override
                            protected void configure() {
                                bind(Foo.class).toInstance(foo);
                            }
                            
                            @Provides
                            JettyConfig getConfig() {
                                // Use emphemeral ports
                                return new DefaultJettyConfig().setPort(0);
                            }
                        }))
                        .createInjector();

        // Determine the emphermal port from jetty
        Server server = injector.getInstance(Server.class);
        int port = ((ServerConnector)server.getConnectors()[0]).getLocalPort();
        
        SampleResource resource = injector.getInstance(SampleResource.class);
        Assert.assertEquals(1, resource.getPostConstructCount());
        Assert.assertEquals(0, resource.getPreDestroyCount());
        
        System.out.println("Listening on port : "+ port);
        URL url = new URL(String.format("http://localhost:%d/kill", port));
        HttpURLConnection conn = (HttpURLConnection)url.openConnection();
        try {
            conn.getResponseCode();
        }
        catch (Exception e) {
            
        }
        injector.awaitTermination();
        
        Mockito.verify(foo, Mockito.times(1)).shutdown();
        Assert.assertEquals(1, resource.getPostConstructCount());
        Assert.assertEquals(1, resource.getPreDestroyCount());
    }
}
