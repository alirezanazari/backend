package com.redbank.gateway.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.route.RouteDefinitionLocator;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = "redbank.gateway.app-encryption.enabled=false"
)
class GatewayRoutesTest {
    @Autowired
    private RouteDefinitionLocator routeDefinitionLocator;

    @Test
    void configuresEveryPublicServiceRoute() {
        Map<String, RouteDefinition> routes = routeDefinitionLocator.getRouteDefinitions()
                .collectList()
                .block()
                .stream()
                .collect(Collectors.toMap(RouteDefinition::getId, Function.identity()));

        assertThat(routes).containsKeys(
                "iam-service",
                "account-service",
                "card-service",
                "kyc-service",
                "feature-flag-service",
                "transfer-service",
                "pfm-service"
        );
        assertThat(routes.get("account-service").getPredicates().getFirst().getArgs().values())
                .contains("/api/v1/account/**", "/api/v1/profile/**");
        assertThat(routes.get("pfm-service").getPredicates().getFirst().getArgs().values())
                .contains("/api/v1/pfm/**");
    }
}
