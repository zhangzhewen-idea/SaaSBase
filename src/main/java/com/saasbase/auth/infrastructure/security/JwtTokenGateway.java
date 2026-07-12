package com.saasbase.auth.infrastructure.security;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.saasbase.auth.domain.UserPrincipal;
import com.saasbase.auth.domain.gateway.TokenGateway;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Component
public class JwtTokenGateway implements TokenGateway {

    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    private final ObjectMapper objectMapper;
    private final byte[] secretKey;
    private final Duration tokenTtl;

    public JwtTokenGateway(
            @Value("${saasbase.security.jwt.secret:01234567890123456789012345678901}") String secretKey,
            @Value("${saasbase.security.jwt.ttl:PT15M}") Duration tokenTtl) {
        this.objectMapper = new ObjectMapper();
        this.secretKey = secretKey.getBytes(StandardCharsets.UTF_8);
        this.tokenTtl = tokenTtl;
    }

    @Override
    public String issueAccessToken(UserPrincipal principal) {
        try {
            Instant issuedAt = Instant.now();
            Instant expiresAt = issuedAt.plus(tokenTtl);

            Map<String, Object> header = Map.of("alg", "HS256", "typ", "JWT");
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("sub", String.valueOf(principal.userId()));
            payload.put("tenant_id", principal.tenantId());
            payload.put("username", principal.username());
            payload.put("jti", UUID.randomUUID().toString());
            payload.put("permissions", new ArrayList<>(principal.permissions()));
            payload.put("iat", issuedAt.getEpochSecond());
            payload.put("exp", expiresAt.getEpochSecond());

            String headerPart = encodeJson(header);
            String payloadPart = encodeJson(payload);
            String signingInput = headerPart + "." + payloadPart;
            return signingInput + "." + encodeSignature(signingInput);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to issue access token", ex);
        }
    }

    @Override
    public UserPrincipal parseAccessToken(String token) {
        Map<String, Object> payload = parsePayload(token);
        Long userId = Long.valueOf(String.valueOf(payload.get("sub")));
        Long tenantId = Long.valueOf(String.valueOf(payload.get("tenant_id")));
        String username = String.valueOf(payload.get("username"));
        Set<String> permissions = extractPermissions(payload.get("permissions"));
        return new UserPrincipal(userId, tenantId, username, permissions);
    }

    @Override
    public String parseTokenId(String token) {
        Object tokenId = parsePayload(token).get("jti");
        if (tokenId == null || String.valueOf(tokenId).isBlank()) {
            throw new IllegalArgumentException("Token id is missing");
        }
        return String.valueOf(tokenId);
    }

    private Map<String, Object> parsePayload(String token) {
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            throw new IllegalArgumentException("Invalid token format");
        }

        String signingInput = parts[0] + "." + parts[1];
        if (!constantTimeEquals(parts[2], encodeSignature(signingInput))) {
            throw new IllegalArgumentException("Invalid token signature");
        }

        try {
            Map<String, Object> payload = objectMapper.readValue(decode(parts[1]), Map.class);
            validateExpiry(payload.get("exp"));
            return payload;
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Invalid token payload", ex);
        }
    }

    private String encodeJson(Object value) throws JsonProcessingException {
        return ENCODER.encodeToString(objectMapper.writeValueAsBytes(value));
    }

    private String encodeSignature(String signingInput) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secretKey, "HmacSHA256"));
            return ENCODER.encodeToString(mac.doFinal(signingInput.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to sign token", ex);
        }
    }

    private String decode(String value) {
        return new String(DECODER.decode(value), StandardCharsets.UTF_8);
    }

    private void validateExpiry(Object expValue) {
        long exp = Long.parseLong(String.valueOf(expValue));
        if (Instant.now().getEpochSecond() >= exp) {
            throw new IllegalArgumentException("Token expired");
        }
    }

    private Set<String> extractPermissions(Object permissionsValue) {
        if (permissionsValue instanceof List<?> permissionsList) {
            return permissionsList.stream().map(String::valueOf).collect(java.util.stream.Collectors.toUnmodifiableSet());
        }
        if (permissionsValue instanceof Set<?> permissionsSet) {
            return permissionsSet.stream().map(String::valueOf).collect(java.util.stream.Collectors.toUnmodifiableSet());
        }
        return Set.of();
    }

    private boolean constantTimeEquals(String left, String right) {
        return java.security.MessageDigest.isEqual(left.getBytes(StandardCharsets.UTF_8), right.getBytes(StandardCharsets.UTF_8));
    }
}
