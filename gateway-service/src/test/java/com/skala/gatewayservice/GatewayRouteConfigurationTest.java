package com.skala.gatewayservice;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.filter.FilterDefinition;
import org.springframework.cloud.gateway.handler.predicate.PredicateDefinition;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.route.RouteDefinitionLocator;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "USER_SERVICE_URL=http://127.0.0.1:10081",
                "ORDER_SERVICE_URL=http://127.0.0.1:10082"
        })
@ActiveProfiles("test")
class GatewayRouteConfigurationTest {

    @Autowired
    private RouteDefinitionLocator routeDefinitionLocator;

    @Test
    void loadsExactlyTheExpectedRoutes() {
        List<RouteDefinition> routes = routeDefinitionLocator.getRouteDefinitions().collectList().block();

        assertThat(routes).isNotNull().hasSize(3);
        Map<String, RouteDefinition> routesById = routes.stream()
                .collect(Collectors.toMap(RouteDefinition::getId, Function.identity()));

        assertRoute(routesById, "user-service-customers", "http://127.0.0.1:10081", "/api/customers/**");
        assertRoute(routesById, "order-service-products", "http://127.0.0.1:10082", "/api/products/**");
        assertRoute(routesById, "order-service-orders", "http://127.0.0.1:10082", "/api/orders/**");

        assertThat(routesById).doesNotContainKey("internal");
        assertThat(routes)
                .flatExtracting(RouteDefinition::getPredicates)
                .extracting(PredicateDefinition::toString)
                .noneMatch(predicate -> predicate.contains("/internal/**"));
        assertThat(routes)
                .flatExtracting(RouteDefinition::getFilters)
                .extracting(FilterDefinition::getName)
                .doesNotContain("RewritePath", "StripPrefix");
    }

    private static void assertRoute(
            Map<String, RouteDefinition> routesById, String id, String uri, String pathPattern) {
        RouteDefinition route = routesById.get(id);

        assertThat(route).as("route %s", id).isNotNull();
        assertThat(route.getUri()).isEqualTo(URI.create(uri));
        assertThat(route.getPredicates())
                .singleElement()
                .satisfies(predicate -> {
                    assertThat(predicate.getName()).isEqualTo("Path");
                    assertThat(predicate.getArgs()).containsValue(pathPattern);
                });
    }
}
