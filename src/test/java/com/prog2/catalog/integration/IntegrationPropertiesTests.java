package com.prog2.catalog.integration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.mock.env.MockEnvironment;

import com.prog2.catalog.integration.infrastructure.config.IntegrationProperties;

import static org.assertj.core.api.Assertions.*;

class IntegrationPropertiesTests {

    private MockEnvironment validEnvironment() {
        return new MockEnvironment().withProperty("CATEDRA_BASE_URL", "https://central.test")
                .withProperty("CATEDRA_USERNAME", "test-user")
                .withProperty("CATEDRA_PASSWORD", TechnicalIntegrationTestServer.PASSWORD)
                .withProperty("CATEDRA_GROUP_ID", "test-group");
    }

    @ParameterizedTest
    @ValueSource(strings = {"CATEDRA_BASE_URL", "CATEDRA_USERNAME", "CATEDRA_PASSWORD", "CATEDRA_GROUP_ID"})
    void rejectsMissingAndBlankConfigurationWithoutDisclosingValues(String variable) {
        var environment = validEnvironment();
        environment.setProperty(variable, " ");
        assertThatThrownBy(() -> IntegrationProperties.from(environment))
                .isInstanceOf(BeanCreationException.class).hasMessageContaining(variable)
                .hasMessageNotContaining(TechnicalIntegrationTestServer.PASSWORD);
        var missing = validEnvironment();
        ((java.util.Map<?, ?>) missing.getPropertySources().get("mockProperties").getSource())
                .remove(variable);
        assertThatThrownBy(() -> IntegrationProperties.from(missing))
                .isInstanceOf(BeanCreationException.class)
                .hasMessageContaining(variable)
                .hasMessageNotContaining(TechnicalIntegrationTestServer.PASSWORD);
    }

    @ParameterizedTest
    @ValueSource(strings = {"invalid-url", "ftp://central.test", "https://user:fictional-secret@central.test",
            "https://central.test?token=fictional-secret", "https://central.test#fragment",
            "http://central.test:99999"})
    void rejectsInvalidOrCredentialBearingUrlWithoutLoggingIt(String url) {
        var environment = validEnvironment().withProperty("CATEDRA_BASE_URL", url);
        assertThatThrownBy(() -> IntegrationProperties.from(environment))
                .isInstanceOf(BeanCreationException.class).hasMessageContaining("CATEDRA_BASE_URL")
                .hasMessageNotContaining(url).hasMessageNotContaining("fictional-secret");
    }

    @Test
    void checksContractualLengthsAndNormalizedGroup() {
        var invalidPassword = validEnvironment().withProperty("CATEDRA_PASSWORD", "123");
        assertThatThrownBy(() -> IntegrationProperties.from(invalidPassword)).hasMessageContaining("CATEDRA_PASSWORD");
        var invalidUsername = validEnvironment().withProperty("CATEDRA_USERNAME", "u".repeat(255));
        assertThatThrownBy(() -> IntegrationProperties.from(invalidUsername)).hasMessageContaining("CATEDRA_USERNAME");
        var invalidGroup = validEnvironment().withProperty("CATEDRA_GROUP_ID", "Not normalized");
        assertThatThrownBy(() -> IntegrationProperties.from(invalidGroup)).hasMessageContaining("CATEDRA_GROUP_ID");
        assertThat(IntegrationProperties.from(validEnvironment()).toString())
                .doesNotContain(TechnicalIntegrationTestServer.PASSWORD);
    }
}
