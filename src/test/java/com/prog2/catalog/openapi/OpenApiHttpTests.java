package com.prog2.catalog.openapi;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import com.prog2.catalog.integration.domain.ports.in.GetTechnicalTokenUseCase;
import com.prog2.catalog.security.JwtTestTokens;
import com.prog2.catalog.synchronization.domain.enums.CatalogAvailability;
import com.prog2.catalog.synchronization.domain.model.CatalogStatus;
import com.prog2.catalog.synchronization.domain.ports.in.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = OpenApiTestConfiguration.class, properties = "API_DOCS_ENABLED=true")
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@ActiveProfiles("openapi-test")
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class OpenApiHttpTests {
    private static final String DOCUMENT = "/v3/api-docs";
    private static final String SEARCH = "/api/catalog/professionals";
    private static final String USER_STATUS = "/api/catalog/synchronization";
    private static final String SERVICE_STATUS = "/api/internal/catalog/synchronization";
    private static final String TOKEN = "/api/internal/catedra/token";
    private static final String CENTRAL_TOKEN = "fictional-central-value-not-for-documentation";
    private final MockMvc mvc;
    private final SearchProfessionalsUseCase searchUseCase;
    private final GetProfessionalCategoriesUseCase categories;
    private final GetProfessionalAgendaUseCase agendas;
    private final GetCatalogStatusUseCase statuses;
    private final GetTechnicalTokenUseCase tokens;

    OpenApiHttpTests(MockMvc mvc, SearchProfessionalsUseCase searchUseCase, GetProfessionalCategoriesUseCase categories,
            GetProfessionalAgendaUseCase agendas, GetCatalogStatusUseCase statuses, GetTechnicalTokenUseCase tokens) {
        this.mvc = mvc;
        this.searchUseCase = searchUseCase;
        this.categories = categories;
        this.agendas = agendas;
        this.statuses = statuses;
        this.tokens = tokens;
    }

    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        registry.add("JWT_PUBLIC_KEY_PATH", JwtTestTokens.PUBLIC_KEY_PATH::toString);
    }

    @BeforeEach void fixtures() {
        when(statuses.get()).thenReturn(new CatalogStatus(CatalogAvailability.NO_COPY, false,
                null, null, null, JwtTestTokens.NOW));
        when(tokens.getToken()).thenReturn(CENTRAL_TOKEN);
        clearInvocations(searchUseCase, categories, agendas, statuses, tokens);
    }

    private JsonNode document() throws Exception {
        String json = mvc.perform(get(DOCUMENT)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonMapper.builder().build().readTree(json);
    }

    @Test void documentsExactlyTheSixExistingGetOperations() throws Exception {
        JsonNode document = document();
        assertThat(document.path("openapi").asString()).startsWith("3.1.");
        Set<String> expected = Set.of(SEARCH, "/api/catalog/categories", USER_STATUS,
                "/api/internal/catalog/professionals/{professionalId}", SERVICE_STATUS, TOKEN);
        assertThat(document.path("paths").propertyNames()).containsExactlyInAnyOrderElementsOf(expected);
        for (String path : expected) {
            JsonNode item = document.path("paths").path(path);
            assertThat(item.propertyNames()).containsExactly("get");
            JsonNode responses = item.path("get").path("responses");
            assertThat(responses.propertyNames()).contains("200", "401", "403", "503");
            assertThat(responses.path("401").path("$ref").asString())
                    .isEqualTo("#/components/responses/Unauthorized");
        }
        assertThat(document.path("paths").path(SEARCH).path("get").path("responses").has("400")).isTrue();
        assertThat(document.path("paths").path("/api/catalog/categories").path("get").path("responses").has("400")).isFalse();
        assertThat(document.path("paths").path("/api/internal/catalog/professionals/{professionalId}")
                .path("get").path("responses").has("404")).isTrue();
    }

    @Test void flattensOptionalSearchParametersAndPreservesLimitsAndDefaults() throws Exception {
        JsonNode operation = document().path("paths").path(SEARCH).path("get");
        Set<String> names = new HashSet<>();
        for (JsonNode parameter : operation.path("parameters")) {
            names.add(parameter.path("name").asString());
            assertThat(parameter.path("in").asString()).isEqualTo("query");
            assertThat(parameter.path("required").asBoolean()).isFalse();
            var schema = parameter.path("schema");
            switch (parameter.path("name").asString()) {
                case "categoryId" -> {
                    assertThat(schema.path("type").asString()).isEqualTo("integer");
                    assertThat(schema.path("exclusiveMinimum").asInt(-1))
                            .as("Positive category ID schema: %s", schema).isZero();
                }
                case "name" -> assertThat(schema.path("maxLength").asInt()).isEqualTo(100);
                case "availableOn" -> assertThat(schema.path("format").asString()).isEqualTo("date");
                case "page" -> {
                    assertThat(schema.path("minimum").asInt(-1)).isZero();
                    assertThat(schema.path("default").asInt(-1)).isZero();
                }
                case "size" -> {
                    assertThat(schema.path("minimum").asInt()).isEqualTo(1);
                    assertThat(schema.path("maximum").asInt()).isEqualTo(100);
                    assertThat(schema.path("default").asInt()).isEqualTo(20);
                }
                default -> assertThat(parameter.path("name").asString()).isEqualTo("enabled");
            }
        }
        assertThat(names).containsExactlyInAnyOrder("categoryId", "name", "enabled", "availableOn", "page", "size");
        assertThat(operation.has("requestBody")).isFalse();
    }

    @Test void keepsProfessionalProjectionsDistinctAndDocumentsTheRealProblemCode() throws Exception {
        JsonNode schemas = document().path("components").path("schemas");
        assertThat(schemas.path("CatalogProfessionalSummary").path("properties").has("category")).isTrue();
        assertThat(schemas.path("CatalogProfessionalSummary").path("properties").has("categoryId")).isFalse();
        assertThat(schemas.path("CatalogProfessionalDetails").path("properties").has("categoryId")).isTrue();
        assertThat(schemas.path("CatalogProfessionalDetails").path("properties").has("category")).isFalse();
        assertThat(schemas.path("ProfessionalSearchResponse").path("properties").path("content")
                .path("items").path("$ref").asString())
                .isEqualTo("#/components/schemas/CatalogProfessionalSummary");
        assertThat(schemas.path("ProfessionalAgendaResponse").path("properties").path("professional")
                .path("$ref").asString()).isEqualTo("#/components/schemas/CatalogProfessionalDetails");
        assertThat(schemas.path("CatalogStatusResponse").path("properties").path("appliedVersion")
                .path("type").toString()).contains("integer", "null");
        assertThat(schemas.path("CatalogStatusResponse").path("properties").path("lastCheckedAt")
                .path("type").toString()).contains("string", "null");
        assertThat(schemas.path("ApiProblem").path("properties").propertyNames())
                .containsExactlyInAnyOrder("type", "title", "status", "code", "instance");
        assertThat(schemas.path("CatalogStatusSummaryResponse").path("properties").propertyNames())
                .containsExactlyInAnyOrder("state", "copyAvailable");
        JsonNode state = schemas.path("CatalogStatusSummaryResponse").path("properties").path("state");
        assertThat(state.path("enum").toString()).contains("NO_COPY", "CURRENT", "STALE");
    }

    @Test void usesOneBearerSchemeWithoutEmbeddingTheExistingTechnicalToken() throws Exception {
        JsonNode document = document();
        var security = document.path("components").path("securitySchemes").path("bearerAuth");
        assertThat(security.path("type").asString()).isEqualTo("http");
        assertThat(security.path("scheme").asString()).isEqualTo("bearer");
        assertThat(document.path("security").get(0).has("bearerAuth")).isTrue();
        assertThat(document.toString()).doesNotContain(CENTRAL_TOKEN);
        assertThat(document.path("components").path("schemas").path("TechnicalTokenResponse")
                .path("properties").path("idToken").path("example").asString())
                .isEqualTo("TOKEN_TECNICO_FICTICIO");
    }

    @Test void servesUiAndYamlAnonymouslyWithoutCallingBusinessPorts() throws Exception {
        mvc.perform(get("/swagger-ui.html")).andExpect(status().is3xxRedirection());
        for (String path : new String[]{DOCUMENT, "/v3/api-docs.yaml", "/v3/api-docs/swagger-config",
                "/swagger-ui/index.html", "/swagger-ui/swagger-ui-bundle.js", "/swagger-ui/swagger-initializer.js"}) {
            mvc.perform(get(path)).andExpect(status().isOk());
        }
        mvc.perform(get("/v3/api-docs/swagger-config"))
                .andExpect(jsonPath("$.persistAuthorization").value(false))
                .andExpect(jsonPath("$.url").value(DOCUMENT));
        verifyNoInteractions(searchUseCase, categories, agendas, statuses, tokens);
    }

    @Test void apiPermissionsRemainSeparateAfterTheDocumentIsReadAnonymously() throws Exception {
        document();
        String user = JwtTestTokens.signed(JwtTestTokens.claims("user"));
        String service = JwtTestTokens.signed(JwtTestTokens.claims("service"));
        mvc.perform(get(USER_STATUS)).andExpect(status().isUnauthorized());
        mvc.perform(get(USER_STATUS).header(HttpHeaders.AUTHORIZATION, "Bearer " + user))
                .andExpect(status().isOk()).andExpect(jsonPath("$.state").value("NO_COPY"));
        mvc.perform(get(TOKEN).header(HttpHeaders.AUTHORIZATION, "Bearer " + user)).andExpect(status().isForbidden());
        mvc.perform(get(USER_STATUS).header(HttpHeaders.AUTHORIZATION, "Bearer " + service)).andExpect(status().isForbidden());
        mvc.perform(get(TOKEN).header(HttpHeaders.AUTHORIZATION, "Bearer " + service))
                .andExpect(status().isOk()).andExpect(jsonPath("$.idToken").value(CENTRAL_TOKEN));
        verify(tokens).getToken();
        verify(statuses).get();
    }

    @Test void anonymousDocumentationDoesNotOpenOtherMethodsOrSimilarPaths() throws Exception {
        String user = JwtTestTokens.signed(JwtTestTokens.claims("user"));
        mvc.perform(post(DOCUMENT).header(HttpHeaders.AUTHORIZATION, "Bearer " + user)).andExpect(status().isForbidden());
        mvc.perform(get("/v3/api-docs-extra").header(HttpHeaders.AUTHORIZATION, "Bearer " + user)).andExpect(status().isForbidden());
        mvc.perform(get("/swagger-ui-extra").header(HttpHeaders.AUTHORIZATION, "Bearer " + user)).andExpect(status().isForbidden());
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
        verifyNoInteractions(searchUseCase, categories, agendas, statuses, tokens);
    }
}
