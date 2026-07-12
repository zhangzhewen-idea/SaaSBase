package com.saasbase.tenant.application.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.saasbase.tenant.domain.Tenant;
import com.saasbase.tenant.domain.TenantStatus;
import com.saasbase.tenant.domain.gateway.TenantGateway;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class CreateTenantRequestValidationTest {
    private static jakarta.validation.ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void closeValidatorFactory() {
        validatorFactory.close();
    }

    @Test
    void acceptsValidRequestAndBoundaryValues() {
        assertThat(validator.validate(request("abc", "a", "a", "a", "123456789012"))).isEmpty();
        assertThat(validator.validate(request("a".repeat(64), "a".repeat(128), "a".repeat(64),
                "a".repeat(128), "a".repeat(72)))).isEmpty();
    }

    @Test
    void rejectsInvalidTenantCodes() {
        for (String code : List.of("ab", "-abc", "abc-", "Abc", "ab_c")) {
            assertThat(validator.validate(request(code, "name", "admin", "Admin", "123456789012")))
                    .as(code).isNotEmpty();
        }
    }

    @Test
    void validatesPasswordLength() {
        assertThat(validator.validate(request("abc", "name", "admin", "Admin", "a".repeat(11)))).isNotEmpty();
        assertThat(validator.validate(request("abc", "name", "admin", "Admin", "a".repeat(12)))).isEmpty();
        assertThat(validator.validate(request("abc", "name", "admin", "Admin", "a".repeat(72)))).isEmpty();
        assertThat(validator.validate(request("abc", "name", "admin", "Admin", "a".repeat(73)))).isNotEmpty();
    }

    @Test
    void rejectsOverlongNames() {
        assertThat(validator.validate(request("abc", "a".repeat(129), "admin", "Admin", "123456789012")))
                .isNotEmpty();
        assertThat(validator.validate(request("abc", "name", "admin", "a".repeat(129), "123456789012")))
                .isNotEmpty();
    }

    @Test
    void gatewayQueryNormalizesStringsAndChecksPagination() {
        TenantGateway.Query query = new TenantGateway.Query(" abc ", "   ", TenantStatus.ACTIVE, 1, 100);
        assertThat(query.tenantCode()).isEqualTo("abc");
        assertThat(query.tenantName()).isNull();
        assertThatThrownBy(() -> new TenantGateway.Query(null, null, null, 0, 20))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TenantGateway.Query(null, null, null, 1, 101))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void gatewayPageDefensivelyCopiesItemsAndChecksTotal() {
        Tenant tenant = Tenant.create("abc", "Acme");
        List<Tenant> source = new ArrayList<>(List.of(tenant));
        TenantGateway.Page page = new TenantGateway.Page(source, 1, 1, 20);
        source.clear();
        assertThat(page.items()).containsExactly(tenant);
        assertThatThrownBy(() -> page.items().add(tenant)).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> new TenantGateway.Page(List.of(), -1, 1, 20))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void queryDefaultsAndMapsToGatewayQuery() {
        TenantQuery query = new TenantQuery(" abc ", " Acme ", TenantStatus.DISABLED, null, null);
        assertThat(query.pageNo()).isEqualTo(1);
        assertThat(query.pageSize()).isEqualTo(20);
        assertThat(query.toGatewayQuery()).isEqualTo(
                new TenantGateway.Query("abc", "Acme", TenantStatus.DISABLED, 1, 20));
    }

    @Test
    void responseMapsTenant() {
        Tenant tenant = Tenant.reconstitute(7L, "abc", "Acme", TenantStatus.DISABLED, 3, 4);
        assertThat(TenantResponse.from(tenant)).isEqualTo(
                new TenantResponse(7L, "abc", "Acme", TenantStatus.DISABLED, 3, 4));
    }

    private static CreateTenantRequest request(
            String code, String name, String username, String displayName, String password) {
        return new CreateTenantRequest(code, name, username, displayName, password);
    }
}
