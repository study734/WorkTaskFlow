package com.teamproject.admin.config;

import org.apache.catalina.connector.Connector;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "app.admin.enabled", havingValue = "true")
public class AdminPortConfig implements WebServerFactoryCustomizer<TomcatServletWebServerFactory> {
    private final int port;
    public AdminPortConfig(@Value("${app.admin.port:19092}") int port) { this.port = port; }
    @Override public void customize(TomcatServletWebServerFactory factory) {
        Connector connector = new Connector(TomcatServletWebServerFactory.DEFAULT_PROTOCOL);
        connector.setPort(port);
        connector.setProperty("address", "0.0.0.0");
        factory.addAdditionalTomcatConnectors(connector);
    }
}
