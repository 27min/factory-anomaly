package io.github.min27.factoryanomaly;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class FactoryAnomalyApplication {

    public static void main(String[] args) {
        SpringApplication.run(FactoryAnomalyApplication.class, args);
    }

}
