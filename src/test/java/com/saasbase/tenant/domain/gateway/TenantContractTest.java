package com.saasbase.tenant.domain.gateway;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import com.saasbase.tenant.domain.Tenant;
import com.saasbase.tenant.domain.TenantAuthState;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class TenantContractTest {
    @Test
    void tenantGatewayExposesExactContract() throws NoSuchMethodException {
        assertThat(Arrays.stream(TenantGateway.class.getDeclaredMethods())
                        .filter(method -> !method.isSynthetic() && !java.lang.reflect.Modifier.isPrivate(method.getModifiers())))
                .extracting(Method::getName)
                .containsExactlyInAnyOrder("existsByCode", "insert", "findById", "page", "update");
        assertMethod(TenantGateway.class, "existsByCode", boolean.class, String.class);
        assertMethod(TenantGateway.class, "insert", Tenant.class, Tenant.class, Long.class);
        Method find = assertMethod(TenantGateway.class, "findById", Optional.class, Long.class);
        assertThat(find.getGenericReturnType().getTypeName()).isEqualTo("java.util.Optional<com.saasbase.tenant.domain.Tenant>");
        assertMethod(TenantGateway.class, "page", TenantGateway.Page.class, TenantGateway.Query.class);
        assertMethod(TenantGateway.class, "update", boolean.class, Tenant.class, Long.class);

        assertThat(TenantGateway.Query.class.getRecordComponents())
                .extracting(component -> component.getType().getName())
                .containsExactly("java.lang.String", "java.lang.String",
                        "com.saasbase.tenant.domain.TenantStatus", "long", "long");
        assertThat(TenantGateway.Page.class.getRecordComponents())
                .extracting(component -> component.getType().getName())
                .containsExactly("java.util.List", "long", "long", "long");
        assertThat(TenantGateway.Page.class.getRecordComponents()[0].getGenericType().getTypeName())
                .isEqualTo("java.util.List<com.saasbase.tenant.domain.Tenant>");
    }

    @Test
    void supportingGatewaysExposeExactContracts() throws NoSuchMethodException {
        assertMethod(TenantAdminInitializer.class, "initialize", void.class,
                Long.class, String.class, String.class, String.class, Long.class);
        assertThat(TenantAdminInitializer.class.getDeclaredMethods()).hasSize(1);
        assertMethod(TenantAuthStateGateway.class, "requireCurrent", TenantAuthState.class, Long.class);
        assertMethod(TenantAuthStateGateway.class, "cache", void.class, TenantAuthState.class);
        assertThat(TenantAuthStateGateway.class.getDeclaredMethods()).hasSize(2);
    }

    @Test
    void domainGatewaysDoNotDependOnApplicationOrCommonApi() {
        noClasses().that().resideInAPackage("com.saasbase.tenant.domain.gateway..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "com.saasbase.tenant.application..", "com.saasbase.common.api..")
                .check(new ClassFileImporter().importPackages("com.saasbase.tenant.domain.gateway"));
    }

    private static Method assertMethod(
            Class<?> owner, String name, Class<?> returnType, Class<?>... parameterTypes)
            throws NoSuchMethodException {
        Method method = owner.getDeclaredMethod(name, parameterTypes);
        assertThat(method.getReturnType()).isEqualTo(returnType);
        return method;
    }
}
