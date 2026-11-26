package com.ravita.portal.config;

import com.ravita.portal.web.BffException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.ResourceAccessException;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

import static org.assertj.core.api.Assertions.assertThat;

/** Como cada falha de rede vira resposta do BFF. */
class ServiceClientsTest {

    @Test
    void serviceThatIsNotThereIs503() {
        assertThat(status(new ConnectException("Connection refused"))).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(status(new UnknownHostException("offer-service"))).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        // container parado com o IP antigo ainda no cache de DNS da JVM
        assertThat(status(new SocketTimeoutException("Connect timed out"))).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void serviceThatAcceptedButDidNotAnswerIs504() {
        BffException error = ServiceClients.failure("offer-service",
                new ResourceAccessException("I/O error", new SocketTimeoutException("Read timed out")));

        assertThat(error.getStatus()).isEqualTo(HttpStatus.GATEWAY_TIMEOUT);
        assertThat(error.getCode()).isEqualTo("DEPENDENCY_TIMEOUT");
    }

    private static HttpStatus status(IOException cause) {
        return ServiceClients.failure("offer-service", new ResourceAccessException("I/O error", cause)).getStatus();
    }
}
