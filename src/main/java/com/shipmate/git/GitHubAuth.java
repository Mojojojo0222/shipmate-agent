package com.shipmate.git;

import com.shipmate.config.AgentProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

@Component
public class GitHubAuth {

    private static final Logger log = LoggerFactory.getLogger(GitHubAuth.class);

    private final AgentProperties props;
    private final ObjectMapper mapper;
    private final OkHttpClient http;

    private volatile String cachedToken;
    private volatile long cachedExpiryEpoch = 0;

    public GitHubAuth(AgentProperties props, ObjectMapper mapper) {
        this.props = props;
        this.mapper = mapper;
        this.http = new OkHttpClient.Builder()
                .connectTimeout(Duration.ofSeconds(10))
                .readTimeout(Duration.ofSeconds(30))
                .build();
    }

    public boolean isAppMode() {
        return props.getGithub().getAppId() != null
                && !props.getGithub().getAppId().isEmpty()
                && props.getGithub().getPrivateKey() != null
                && !props.getGithub().getPrivateKey().isEmpty();
    }

    public synchronized String getToken() {
        if (!isAppMode()) {
            return props.getGithub().getToken();
        }
        if (cachedToken != null && Instant.now().getEpochSecond() < cachedExpiryEpoch - 60) {
            return cachedToken;
        }
        cachedToken = fetchInstallationToken();
        return cachedToken;
    }

    private String fetchInstallationToken() {
        String jwt = createJwt();
        try {
            Request req = new Request.Builder()
                    .url(props.getGithub().getApiUrl() + "/app/installations/" + props.getGithub().getInstallationId() + "/access_tokens")
                    .header("Accept", "application/vnd.github+json")
                    .header("Authorization", "Bearer " + jwt)
                    .header("X-GitHub-Api-Version", "2022-11-28")
                    .header("User-Agent", "shipmate-agent")
                    .post(RequestBody.create("{}", MediaType.get("application/json; charset=utf-8")))
                    .build();
            try (Response resp = http.newCall(req).execute()) {
                String body = resp.body() == null ? "" : resp.body().string();
                if (!resp.isSuccessful()) {
                    throw new IllegalStateException("installation token failed " + resp.code() + ": " + body);
                }
                JsonNode node = mapper.readTree(body);
                cachedExpiryEpoch = Instant.parse(node.path("expires_at").asText()).getEpochSecond();
                log.info("GitHub App installation token acquired, expires in {} min",
                        (cachedExpiryEpoch - Instant.now().getEpochSecond()) / 60);
                return node.path("token").asText();
            }
        } catch (Exception e) {
            throw new IllegalStateException("cannot acquire GitHub App token: " + e.getMessage(), e);
        }
    }

    private String createJwt() {
        try {
            String pem = loadPem();
            String base64 = pem
                    .replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replaceAll("\\s", "");
            byte[] der = Base64.getDecoder().decode(base64);
            PrivateKey key = KeyFactory.getInstance("RSA")
                    .generatePrivate(new PKCS8EncodedKeySpec(der));

            long now = Instant.now().getEpochSecond();
            String header = base64url("{\"alg\":\"RS256\",\"typ\":\"JWT\"}");
            String payload = base64url("{\"iat\":" + now + ",\"exp\":" + (now + 540)
                    + ",\"iss\":\"" + props.getGithub().getAppId() + "\"}");

            Signature sig = Signature.getInstance("SHA256withRSA");
            sig.initSign(key);
            sig.update((header + "." + payload).getBytes(StandardCharsets.US_ASCII));
            return header + "." + payload + "." + base64url(sig.sign());
        } catch (Exception e) {
            throw new IllegalStateException("cannot sign GitHub App JWT: " + e.getMessage(), e);
        }
    }

    private String loadPem() throws Exception {
        String raw = props.getGithub().getPrivateKey();
        if (raw.contains("-----BEGIN")) {
            return raw;
        }
        return Files.readString(Path.of(raw));
    }

    private static String base64url(byte[] data) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(data);
    }

    private static String base64url(String s) {
        return base64url(s.getBytes(StandardCharsets.US_ASCII));
    }
}
