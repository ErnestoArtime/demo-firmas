package com.example.demo.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.annotation.PostConstruct;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    private static final String MODE_PLAIN = "plain";
    private static final String MODE_HMAC = "hmac";

    private final ApiKeySecurityProperties properties;
    private final ObjectMapper objectMapper;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();
    private final ConcurrentHashMap<String, Long> nonceCache = new ConcurrentHashMap<>();

    private List<String> normalizedKeys = List.of();
    private Map<String, String> hmacCredentials = Map.of();

    public ApiKeyAuthFilter(
            ApiKeySecurityProperties properties,
            ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void validateConfiguration() {
        this.normalizedKeys = properties.getKeys().stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .distinct()
                .toList();

        this.hmacCredentials = parseHmacCredentials(properties.getHmacClients());

        if (!properties.isEnabled()) {
            return;
        }

        String mode = mode();
        if (MODE_PLAIN.equals(mode) && normalizedKeys.isEmpty()) {
            throw new IllegalStateException("API Key en modo plain habilitada pero no hay claves configuradas");
        }
        if (MODE_HMAC.equals(mode) && hmacCredentials.isEmpty()) {
            throw new IllegalStateException("API Key en modo hmac habilitada pero no hay clientes configurados");
        }
        if (!MODE_PLAIN.equals(mode) && !MODE_HMAC.equals(mode)) {
            throw new IllegalStateException("Modo de API Key no soportado: " + mode);
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!properties.isEnabled()) {
            return true;
        }

        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String path = pathWithinApplication(request);
        for (String pattern : properties.getPublicPaths()) {
            if (pathMatcher.match(pattern, path)) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        if (properties.isRequireHttps() && !isHttpsRequest(request)) {
            reject(response, HttpStatus.FORBIDDEN, "Se requiere HTTPS para esta integracion", request);
            return;
        }

        boolean authorized = switch (mode()) {
            case MODE_HMAC -> isAuthorizedHmac(request);
            case MODE_PLAIN -> isAuthorizedPlain(request.getHeader(properties.getHeaderName()));
            default -> false;
        };

        if (!authorized) {
            reject(response, HttpStatus.UNAUTHORIZED, "API Key invalida o ausente", request);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean isAuthorizedPlain(String providedKey) {
        if (providedKey == null || providedKey.isBlank()) {
            return false;
        }
        byte[] provided = providedKey.trim().getBytes(StandardCharsets.UTF_8);
        for (String allowed : normalizedKeys) {
            byte[] candidate = allowed.getBytes(StandardCharsets.UTF_8);
            if (MessageDigest.isEqual(provided, candidate)) {
                return true;
            }
        }
        return false;
    }

    private boolean isAuthorizedHmac(HttpServletRequest request) {
        String keyId = header(request, properties.getKeyIdHeaderName());
        String signature = header(request, properties.getSignatureHeaderName());
        String timestampValue = header(request, properties.getTimestampHeaderName());
        String nonce = header(request, properties.getNonceHeaderName());

        if (keyId.isBlank() || signature.isBlank() || timestampValue.isBlank()) {
            return false;
        }
        if (properties.isRequireNonce() && nonce.isBlank()) {
            return false;
        }

        String secret = hmacCredentials.get(keyId);
        if (secret == null || secret.isBlank()) {
            return false;
        }

        long timestamp;
        try {
            timestamp = Long.parseLong(timestampValue);
        } catch (NumberFormatException ex) {
            return false;
        }

        long now = Instant.now().getEpochSecond();
        long skew = Math.abs(now - timestamp);
        if (skew > Math.max(1, properties.getMaxSkewSeconds())) {
            return false;
        }

        if (properties.isRequireNonce()) {
            cleanupExpiredNonces(now);
            String nonceKey = keyId + ":" + timestamp + ":" + nonce;
            long expiresAt = now + Math.max(1, properties.getMaxSkewSeconds());
            Long previous = nonceCache.putIfAbsent(nonceKey, expiresAt);
            if (previous != null) {
                return false;
            }
        }

        String canonical = canonicalRequest(request, timestampValue, nonce);
        String expectedSignature;
        try {
            expectedSignature = hmacSha256Hex(secret, canonical);
        } catch (GeneralSecurityException ex) {
            return false;
        }

        byte[] expectedBytes = expectedSignature.getBytes(StandardCharsets.UTF_8);
        byte[] providedBytes = signature.trim().toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expectedBytes, providedBytes);
    }

    private void cleanupExpiredNonces(long nowEpochSeconds) {
        nonceCache.entrySet().removeIf(entry -> entry.getValue() < nowEpochSeconds);
    }

    @Scheduled(fixedDelayString = "${app.security.api-key.nonce-cleanup-interval-ms:60000}")
    void scheduledNonceCleanup() {
        if (!properties.isEnabled() || !MODE_HMAC.equals(mode())) {
            return;
        }
        cleanupExpiredNonces(Instant.now().getEpochSecond());
    }

    private String canonicalRequest(HttpServletRequest request, String timestamp, String nonce) {
        String method = request.getMethod().toUpperCase(Locale.ROOT);
        String path = pathWithinApplication(request);
        String query = request.getQueryString() == null ? "" : request.getQueryString();
        return method + "\n" + path + "\n" + query + "\n" + timestamp + "\n" + nonce;
    }

    private String hmacSha256Hex(String secret, String payload) throws GeneralSecurityException {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] bytes = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        return toHex(bytes);
    }

    private String toHex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            builder.append(String.format("%02x", b));
        }
        return builder.toString();
    }

    private Map<String, String> parseHmacCredentials(List<String> clients) {
        Map<String, String> result = new HashMap<>();
        for (String item : clients) {
            if (item == null || item.isBlank()) {
                continue;
            }
            int idx = item.indexOf(':');
            if (idx <= 0 || idx >= item.length() - 1) {
                continue;
            }
            String id = item.substring(0, idx).trim();
            String secret = item.substring(idx + 1).trim();
            if (!id.isBlank() && !secret.isBlank()) {
                result.put(id, secret);
            }
        }
        return Map.copyOf(result);
    }

    private boolean isHttpsRequest(HttpServletRequest request) {
        if (request.isSecure()) {
            return true;
        }
        String forwardedProto = request.getHeader("X-Forwarded-Proto");
        return forwardedProto != null && forwardedProto.toLowerCase(Locale.ROOT).startsWith("https");
    }

    private String mode() {
        return properties.getMode() == null
                ? MODE_PLAIN
                : properties.getMode().trim().toLowerCase(Locale.ROOT);
    }

    private String header(HttpServletRequest request, String name) {
        if (name == null || name.isBlank()) {
            return "";
        }
        String value = request.getHeader(name);
        return value == null ? "" : value.trim();
    }

    private void reject(
            HttpServletResponse response,
            HttpStatus status,
            String message,
            HttpServletRequest request) throws IOException {
        response.setStatus(status.value());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        Map<String, Object> body = Map.of(
                "timestamp", Instant.now(),
                "status", status.value(),
                "error", status.getReasonPhrase(),
                "message", message,
                "path", request.getRequestURI());
        objectMapper.writeValue(response.getWriter(), body);
    }

    private String pathWithinApplication(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isBlank() && uri.startsWith(contextPath)) {
            return uri.substring(contextPath.length());
        }
        return uri;
    }
}