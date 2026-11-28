package az.fitnest.user.client.dashboard;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;
/**
 * HTTP client to sibling dashboard controllers. Forwards gateway identity headers
 * so {@code @PreAuthorize} on those services still sees the admin caller.
 */
@Component
@Slf4j
public class DashboardSiblingClient {

    private final RestClient paymentDashboardRestClient;
    private final RestClient catalogDashboardRestClient;
    private final RestClient orderDashboardRestClient;

    public DashboardSiblingClient(
            @Qualifier("paymentDashboardRestClient") RestClient paymentDashboardRestClient,
            @Qualifier("catalogDashboardRestClient") RestClient catalogDashboardRestClient,
            @Qualifier("orderDashboardRestClient") RestClient orderDashboardRestClient) {
        this.paymentDashboardRestClient = paymentDashboardRestClient;
        this.catalogDashboardRestClient = catalogDashboardRestClient;
        this.orderDashboardRestClient = orderDashboardRestClient;
    }

    public Map<String, Object> fetchIncomeSummary(LocalDate dateFrom, LocalDate dateTo) {
        return get(paymentDashboardRestClient, "/api/v1/admin/dashboard/income-summary", dateFrom, dateTo, null);
    }

    public Map<String, Object> fetchGymObligations(LocalDate dateFrom, LocalDate dateTo) {
        return get(catalogDashboardRestClient, "/api/v1/admin/dashboard/gym-obligations", dateFrom, dateTo, null);
    }

    public Map<String, Object> fetchTopGyms(LocalDate dateFrom, LocalDate dateTo, int limit) {
        return get(catalogDashboardRestClient, "/api/v1/admin/dashboard/top-gyms", dateFrom, dateTo, limit);
    }

    public Map<String, Object> fetchSubscriptionSummary(LocalDate dateFrom, LocalDate dateTo) {
        return get(orderDashboardRestClient, "/api/v1/admin/dashboard/subscriptions/summary", dateFrom, dateTo, null);
    }

    public List<Map<String, Object>> fetchPurchaseDates(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return List.of();
        }
        try {
            List<Map<String, Object>> body = orderDashboardRestClient.get()
                    .uri(uriBuilder -> {
                        var b = uriBuilder.path("/api/v1/admin/dashboard/subscriptions/purchase-dates");
                        for (Long id : userIds) {
                            b = b.queryParam("userIds", id);
                        }
                        return b.build();
                    })
                    .headers(this::copyIdentityHeaders)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});
            return body != null ? body : List.of();
        } catch (Exception e) {
            log.warn("Dashboard purchase-dates call failed: {}", e.getMessage());
            return List.of();
        }
    }

    private Map<String, Object> get(
            RestClient client,
            String path,
            LocalDate dateFrom,
            LocalDate dateTo,
            Integer limit) {
        try {
            Map<String, Object> body = client.get()
                    .uri(uriBuilder -> {
                        var b = uriBuilder.path(path);
                        if (dateFrom != null) {
                            b = b.queryParam("dateFrom", dateFrom.toString());
                        }
                        if (dateTo != null) {
                            b = b.queryParam("dateTo", dateTo.toString());
                        }
                        if (limit != null) {
                            b = b.queryParam("limit", limit);
                        }
                        return b.build();
                    })
                    .headers(this::copyIdentityHeaders)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});
            return body != null ? body : Collections.emptyMap();
        } catch (Exception e) {
            throw new SiblingCallException(path, e);
        }
    }

    private void copyIdentityHeaders(org.springframework.http.HttpHeaders headers) {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) {
            return;
        }
        HttpServletRequest request = attrs.getRequest();
        copy(headers, request, "X-User-Id");
        copy(headers, request, "X-Scopes");
        copy(headers, request, "X-User-Roles");
        copy(headers, request, "X-From-Gateway");
        copy(headers, request, "X-Request-Id");
        copy(headers, request, "Accept-Language");
    }

    private static void copy(org.springframework.http.HttpHeaders headers, HttpServletRequest request, String name) {
        String value = request.getHeader(name);
        if (value != null && !value.isBlank()) {
            headers.set(name, value);
        }
    }

    public static class SiblingCallException extends RuntimeException {
        public SiblingCallException(String path, Throwable cause) {
            super("Failed calling " + path + ": " + cause.getMessage(), cause);
        }
    }
}
