package com.ravita.admin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.security.Security;

@SpringBootApplication
public class AdminBffApplication {

    public static void main(String[] args) {
        // cache de DNS curto: quando um serviço é recriado e ganha outro IP, o BFF o encontra em até 5 s
        // (o padrão da JVM é 30 s, e nesse tempo as chamadas iriam para o IP antigo e virariam 503)
        Security.setProperty("networkaddress.cache.ttl", "5");
        Security.setProperty("networkaddress.cache.negative.ttl", "1");
        SpringApplication.run(AdminBffApplication.class, args);
    }
}
