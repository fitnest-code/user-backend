package az.fitnest.user.configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class DashboardRestClientConfig {

    @Bean
    RestClient paymentDashboardRestClient(
            @Value("${fitnest.services.payment-base-url:http://payment-backend:8080}") String baseUrl) {
        return RestClient.builder().baseUrl(baseUrl).build();
    }

    @Bean
    RestClient catalogDashboardRestClient(
            @Value("${fitnest.services.catalog-base-url:http://catalog-backend:8080}") String baseUrl) {
        return RestClient.builder().baseUrl(baseUrl).build();
    }

    @Bean
    RestClient orderDashboardRestClient(
            @Value("${fitnest.services.order-base-url:http://order-backend:8080}") String baseUrl) {
        return RestClient.builder().baseUrl(baseUrl).build();
    }
}
