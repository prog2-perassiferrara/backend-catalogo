package com.prog2.catalog.infrastructure.security.config;

import java.time.Clock;
import java.time.Duration;
import java.util.List;

import jakarta.servlet.DispatcherType;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtAudienceValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import tools.jackson.databind.json.JsonMapper;

import com.prog2.catalog.infrastructure.security.jwt.JwtContractValidator;
import com.prog2.catalog.infrastructure.security.web.SecurityProblemHandler;

/**
 * Configura autenticación JWT y autorización HTTP transversal en infraestructura.
 * Reutiliza los filtros y la criptografía de Spring Security; no implementa un filtro propio.
 * Catálogo solo conoce la pública de Turnos. Las reglas específicas preceden a las generales.
 */
@Configuration(proxyBeanMethods = false)
public class SecurityConfiguration {

    @Bean
    JwtSecurityProperties jwtSecurityProperties(Environment environment) {
        return JwtSecurityProperties.from(environment);
    }

    /**
     * Combina firma RS256, confianza y reglas de vigencia. El reloj existente también se usa
     * en integración, pero la tolerancia de 30 s solo corresponde a los JWT propios.
     *
     * @param properties pública local validada
     * @param clock reloj UTC del proceso, sustituible en pruebas
     * @return decoder sin consultas remotas ni capacidad de firma
     */
    @Bean
    JwtDecoder jwtDecoder(JwtSecurityProperties properties, Clock clock) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(properties.publicKey())
                .signatureAlgorithm(SignatureAlgorithm.RS256).build();
        JwtTimestampValidator timestamps = new JwtTimestampValidator(Duration.ofSeconds(30));
        timestamps.setClock(clock);
        timestamps.setAllowEmptyExpiryClaim(false);
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(timestamps,
                new JwtIssuerValidator("backend-turnos"), new JwtAudienceValidator("catalog"),
                new JwtContractValidator(clock)));
        return decoder;
    }

    /**
     * Habilita solo health anónimo; usuarios y servicio tienen perfiles separados.
     * CSRF se desactiva porque la API usa bearer explícito, sin cookies de autenticación.
     * CORS admite únicamente los orígenes configurados y no concede permisos de negocio.
     *
     * @param http constructor servlet proporcionado por Spring Security
     * @param properties orígenes admitidos
     * @param mapper serializador para errores producidos antes de MVC
     * @return cadena stateless con denegación por defecto
     */
    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    SecurityFilterChain securityFilterChain(HttpSecurity http, JwtSecurityProperties properties,
            JsonMapper mapper) {
        SecurityProblemHandler problems = new SecurityProblemHandler(mapper);
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName("auth");
        authorities.setAuthorityPrefix("");
        JwtAuthenticationConverter authentication = new JwtAuthenticationConverter();
        authentication.setJwtGrantedAuthoritiesConverter(authorities);

        return http
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsSource(properties)))
                .exceptionHandling(errors -> errors.authenticationEntryPoint(problems).accessDeniedHandler(problems))
                .oauth2ResourceServer(resource -> resource
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(authentication))
                        .authenticationEntryPoint(problems).accessDeniedHandler(problems))
                .authorizeHttpRequests(routes -> routes
                        // Permite el despacho interno de un error, no una llamada externa a /error.
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(HttpMethod.GET, "/actuator/health").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/internal/catedra/token")
                            .access((identity, _) -> new AuthorizationDecision(
                                    identity.get() instanceof JwtAuthenticationToken jwt
                                    && "service".equals(jwt.getToken().getClaimAsString("token_use"))
                                    && "backend-turnos".equals(jwt.getName())
                                    && jwt.getAuthorities().stream().anyMatch(
                                            role -> "ROLE_CATALOG_INTEGRATION".equals(role.getAuthority()))))
                        .requestMatchers("/api/internal/**").denyAll()
                        .requestMatchers("/api/**")
                            .access((identity, _) -> new AuthorizationDecision(
                                    identity.get() instanceof JwtAuthenticationToken jwt
                                    && "user".equals(jwt.getToken().getClaimAsString("token_use"))
                                    && jwt.getAuthorities().stream().anyMatch(
                                            role -> "ROLE_USER".equals(role.getAuthority()))))
                        .anyRequest().denyAll())
                .build();
    }

    private static UrlBasedCorsConfigurationSource corsSource(JwtSecurityProperties properties) {
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOrigins(properties.allowedOrigins());
        cors.setAllowedMethods(List.of("GET", "OPTIONS"));
        cors.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        cors.setAllowCredentials(false);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cors);
        return source;
    }
}
