package com.prog2.catalog.security;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSObject;
import com.nimbusds.jose.Payload;
import com.nimbusds.jose.crypto.RSASSASigner;

/** Firmante exclusivamente de prueba: pares efímeros, sin claves persistidas en el repositorio. */
public final class JwtTestTokens {

    public static final Instant NOW = Instant.parse("2026-10-14T12:00:00Z");
    public static final KeyPair KEYS = rsaKeyPair(2048);
    public static final Path PUBLIC_KEY_PATH = publicKeyFile();

    private JwtTestTokens() {
    }

    public static KeyPair rsaKeyPair(int bits) {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(bits);
            return generator.generateKeyPair();
        } catch (java.security.GeneralSecurityException failure) {
            throw new IllegalStateException("Cannot generate fictional test keys", failure);
        }
    }

    public static String pem(String label, byte[] encoded) {
        return "-----BEGIN " + label + "-----\n"
                + Base64.getMimeEncoder(64, new byte[] {'\n'}).encodeToString(encoded)
                + "\n-----END " + label + "-----\n";
    }

    private static Path publicKeyFile() {
        try {
            // Usar el directorio de build del proyecto, no el temporal compartido del sistema.
            Path directory = Files.createDirectories(Path.of("target", "test-keys").toAbsolutePath());
            Path path = Files.createTempFile(directory, "catalog-test-public-", ".pem");
            path.toFile().deleteOnExit();
            return Files.writeString(path, pem("PUBLIC KEY", KEYS.getPublic().getEncoded()), StandardCharsets.US_ASCII);
        } catch (java.io.IOException failure) {
            throw new IllegalStateException("Cannot create fictional public key fixture", failure);
        }
    }

    public static Map<String, Object> claims(String profile) {
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("iss", "backend-turnos");
        claims.put("aud", List.of("catalog"));
        claims.put("sub", "service".equals(profile) ? "backend-turnos" : "fictional-user-42");
        claims.put("token_use", profile);
        claims.put("auth", "service".equals(profile) ? "ROLE_CATALOG_INTEGRATION" : "ROLE_USER");
        claims.put("iat", NOW.getEpochSecond());
        claims.put("exp", NOW.plusSeconds(300).getEpochSecond());
        return claims;
    }

    public static String signed(Map<String, Object> claims) throws JOSEException {
        return signed(claims, KEYS, JWSAlgorithm.RS256);
    }

    public static String signed(Map<String, Object> claims, KeyPair keys, JWSAlgorithm algorithm) throws JOSEException {
        JWSObject token = new JWSObject(new JWSHeader(algorithm), new Payload(claims));
        token.sign(new RSASSASigner(keys.getPrivate()));
        return token.serialize();
    }
}
