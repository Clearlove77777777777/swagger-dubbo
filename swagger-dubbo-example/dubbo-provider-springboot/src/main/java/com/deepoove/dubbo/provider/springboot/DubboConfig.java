package com.deepoove.dubbo.provider.springboot;

import com.alibaba.dubbo.config.ApplicationConfig;
import com.alibaba.dubbo.config.ProtocolConfig;
import com.alibaba.dubbo.config.RegistryConfig;
import com.deepoove.swagger.dubbo.annotations.EnableDubboSwagger;
import org.apache.dubbo.config.spring.context.annotation.DubboComponentScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
//@ImportResource({ "classpath:dubbo/*.xml" })
//@PropertySource("classpath:swagger-dubbo.properties")
@DubboComponentScan(basePackages = { "com.deepoove.dubbo.provider.springboot" })
@EnableDubboSwagger
public class DubboConfig {

    @Bean
    public ApplicationConfig applicationConfig() {
        ApplicationConfig applicationConfig = new ApplicationConfig();
        applicationConfig.setName("swagger-dubbo-test");
        applicationConfig.setOwner("dengshangyu");
        return applicationConfig;
    }

    @Bean
    public RegistryConfig registryConfig() {
        RegistryConfig registryConfig = new RegistryConfig();
        registryConfig.setAddress("zookeeper://10.1.100.34:2181");
        registryConfig.setClient("curator");
        registryConfig.setGroup("dubbo/live");
        registryConfig.setProtocol("zookeeper");
        registryConfig.setCheck(Boolean.FALSE);
        return registryConfig;
    }

    @Bean
    public ProtocolConfig protocolConfig() {
        ProtocolConfig protocol = new ProtocolConfig();
        protocol.setName("dubbo");
        protocol.setPort(29880);
        return protocol;
    }

}
