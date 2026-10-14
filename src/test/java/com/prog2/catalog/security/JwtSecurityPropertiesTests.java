package com.prog2.catalog.security;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPairGenerator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.mock.env.MockEnvironment;

import com.prog2.catalog.infrastructure.security.config.JwtSecurityProperties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtSecurityPropertiesTests {

    @TempDir
    Path directory;

    @Test
    void loadsPublicKeyAndDefaultsToNoBrowserOrigins() {
        var properties = JwtSecurityProperties.from(environment());
        assertThat(properties.publicKey()).isEqualTo(JwtTestTokens.KEYS.getPublic());
        assertThat(properties.allowedOrigins()).isEmpty();
        assertThat(properties.toString()).doesNotContain(JwtTestTokens.PUBLIC_KEY_PATH.toString());
    }

    @Test
    void rejectsMissingKeyPath() {
        assertThatThrownBy(() -> JwtSecurityProperties.from(new MockEnvironment()))
                .isInstanceOf(BeanCreationException.class).hasMessageContaining("JWT_PUBLIC_KEY_PATH")
                .hasNoCause();
    }

    @ParameterizedTest
    @ValueSource(strings = {" ", "missing-file.pem", "bad\u0000path"})
    void rejectsBlankMissingOrInvalidPathWithoutEchoingIt(String location) {
        assertThatThrownBy(() -> JwtSecurityProperties.from(environment()
                .withProperty("JWT_PUBLIC_KEY_PATH", location)))
                .isInstanceOf(BeanCreationException.class).hasMessageContaining("JWT_PUBLIC_KEY_PATH")
                .hasNoCause();
    }

    @Test
    void rejectsDirectoryInsteadOfReadablePublicKey() {
        assertThatThrownBy(() -> JwtSecurityProperties.from(environment()
                .withProperty("JWT_PUBLIC_KEY_PATH", directory.toString())))
                .isInstanceOf(BeanCreationException.class).hasNoCause();
    }

    @Test
    void rejectsPrivateMalformedWeakAndNonRsaKeysWithoutDisclosingContents() throws Exception {
        var ec = KeyPairGenerator.getInstance("EC").generateKeyPair();
        for (String content : new String[] {
                "fictional-sensitive-invalid-content",
                JwtTestTokens.pem("PRIVATE KEY", JwtTestTokens.KEYS.getPrivate().getEncoded()),
                JwtTestTokens.pem("PUBLIC KEY", JwtTestTokens.rsaKeyPair(1024).getPublic().getEncoded()),
                JwtTestTokens.pem("PUBLIC KEY", ec.getPublic().getEncoded())}) {
            Path file = Files.writeString(directory.resolve("invalid.pem"), content);
            assertThatThrownBy(() -> JwtSecurityProperties.from(environment()
                    .withProperty("JWT_PUBLIC_KEY_PATH", file.toString())))
                    .isInstanceOf(BeanCreationException.class).hasMessageContaining("JWT_PUBLIC_KEY_PATH")
                    .hasMessageNotContaining(content).hasNoCause();
        }
    }

    @Test
    void acceptsExplicitBrowserOriginsAndTrimsTheirSeparators() {
        var properties = JwtSecurityProperties.from(environment().withProperty("CORS_ALLOWED_ORIGINS",
                "https://app.test, http://localhost:3000"));
        assertThat(properties.allowedOrigins()).containsExactly("https://app.test", "http://localhost:3000");
    }

    @ParameterizedTest
    @ValueSource(strings = {"*", "https://*.test", "invalid", "ftp://app.test", "https://app.test/",
            "https://app.test/path", "https://app.test?token=fictional-secret",
            "https://user:fictional-secret@app.test", "https://app.test#fragment",
            "http://app.test:99999", "http://app.test:0", "https://app.test,", "https://app.test,,https://other.test"})
    void rejectsPermissiveOrMalformedOriginsWithoutEchoingThem(String origin) {
        assertThatThrownBy(() -> JwtSecurityProperties.from(environment()
                .withProperty("CORS_ALLOWED_ORIGINS", origin)))
                .isInstanceOf(BeanCreationException.class).hasMessageContaining("CORS_ALLOWED_ORIGINS")
                .hasMessageNotContaining(origin).hasNoCause();
    }

    private static MockEnvironment environment() {
        return new MockEnvironment().withProperty("JWT_PUBLIC_KEY_PATH", JwtTestTokens.PUBLIC_KEY_PATH.toString());
    }
}
