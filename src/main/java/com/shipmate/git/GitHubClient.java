package com.shipmate.git;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.shipmate.config.AgentProperties;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
public class GitHubClient {

    private static final Logger log = LoggerFactory.getLogger(GitHubClient.class);
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final AgentProperties props;
    private final GitHubAuth auth;
    private final OkHttpClient http;
    private final ObjectMapper mapper;

    public GitHubClient(AgentProperties props, ObjectMapper mapper, GitHubAuth auth) {
        this.props = props;
        this.mapper = mapper;
        this.auth = auth;
        this.http = new OkHttpClient.Builder()
                .connectTimeout(Duration.ofSeconds(10))
                .readTimeout(Duration.ofSeconds(30))
                .writeTimeout(Duration.ofSeconds(30))
                .build();
    }

    private Request.Builder authed(String url) {
        Request.Builder b = new Request.Builder()
                .url(url)
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .header("User-Agent", "shipmate-agent");
        String token = auth.getToken();
        if (token != null && !token.isEmpty()) {
            b.header("Authorization", "Bearer " + token);
        }
        return b;
    }

    private String url(String path) {
        return props.getGithub().getApiUrl() + path;
    }

    private JsonNode exec(Request.Builder builder) throws IOException {
        try (Response resp = http.newCall(builder.build()).execute()) {
            String body = resp.body() == null ? "" : resp.body().string();
            if (!resp.isSuccessful()) {
                throw new IOException("GitHub API " + resp.code() + ": " + body);
            }
            return body.isEmpty() ? null : mapper.readTree(body);
        }
    }

    public boolean repoExists(String owner, String repo) {
        try {
            exec(authed(url("/repos/" + owner + "/" + repo)).get());
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    public String getDefaultBranch(String owner, String repo) {
        try {
            JsonNode node = exec(authed(url("/repos/" + owner + "/" + repo)).get());
            return node.path("default_branch").asText("main");
        } catch (IOException e) {
            return "main";
        }
    }

    public boolean fileExists(String owner, String repo, String path, String branch) {
        try {
            exec(authed(url("/repos/" + owner + "/" + repo + "/contents/" + path + "?ref=" + branch)).get());
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    public FileContent getFile(String owner, String repo, String path, String branch) throws IOException {
        JsonNode node = exec(authed(url("/repos/" + owner + "/" + repo + "/contents/" + path + "?ref=" + branch)).get());
        String content = node.path("content").asText();
        String sha = node.path("sha").asText();
        return new FileContent(new String(Base64.getMimeDecoder().decode(content)), sha);
    }

    public void putFile(String owner, String repo, String path, String message, String content, String existingSha, String branch) throws IOException {
        ObjectNode body = mapper.createObjectNode();
        body.put("message", message);
        body.put("content", Base64.getEncoder().encodeToString(content.getBytes()));
        if (existingSha != null && !existingSha.isEmpty()) {
            body.put("sha", existingSha);
        }
        body.put("branch", branch);
        exec(authed(url("/repos/" + owner + "/" + repo + "/contents/" + path))
                .put(RequestBody.create(body.toString(), JSON)));
    }

    public String getHeadSha(String owner, String repo, String branch) {
        try {
            JsonNode node = exec(authed(url("/repos/" + owner + "/" + repo + "/commits?per_page=1&sha=" + branch)).get());
            if (node.isArray() && node.size() > 0) {
                return node.get(0).path("sha").asText();
            }
        } catch (IOException e) {
            log.warn("getHeadSha failed for {}/{}: {}", owner, repo, e.getMessage());
        }
        return null;
    }

    public String findPreviousCommitForFile(String owner, String repo, String path, String branch) throws IOException {
        JsonNode node = exec(authed(url("/repos/" + owner + "/" + repo + "/commits?path=" + path + "&per_page=5&sha=" + branch)).get());
        if (node.isArray() && node.size() > 1) {
            return node.get(1).path("sha").asText();
        }
        return null;
    }

    public String findPreviousImageTag(String owner, String repo, String path, String branch, String badTag) {
        try {
            JsonNode node = exec(authed(url("/repos/" + owner + "/" + repo + "/commits?path=" + path + "&per_page=10&sha=" + branch)).get());
            if (!node.isArray()) return null;
            String current = badTag;
            for (JsonNode c : node) {
                String sha = c.path("sha").asText();
                try {
                    JsonNode file = exec(authed(url("/repos/" + owner + "/" + repo + "/contents/" + path + "?ref=" + sha)).get());
                    String content = new String(Base64.getMimeDecoder().decode(file.path("content").asText()));
                    String tag = null;
                    for (String line : content.split("\n")) {
                        if (line.trim().startsWith("tag:")) {
                            tag = line.substring(line.indexOf(":") + 1).trim();
                            break;
                        }
                    }
                    if (tag != null && !tag.equals(current)) {
                        return tag;
                    }
                    current = tag == null ? current : tag;
                } catch (IOException e) {
                    log.warn("skip values read at {}: {}", sha, e.getMessage());
                }
            }
        } catch (IOException e) {
            log.warn("findPreviousImageTag failed for {}/{}: {}", owner, repo, e.getMessage());
        }
        return null;
    }

    public boolean dispatchWorkflow(String owner, String repo, String workflowFile, String ref) {
        try {
            ObjectNode body = mapper.createObjectNode();
            body.put("ref", ref);
            exec(authed(url("/repos/" + owner + "/" + repo + "/actions/workflows/" + workflowFile + "/dispatches"))
                    .post(RequestBody.create(body.toString(), JSON)));
            return true;
        } catch (IOException e) {
            log.warn("dispatchWorkflow failed for {}/{}: {}", owner, repo, e.getMessage());
            return false;
        }
    }

    public WorkflowRunStatus getRunStatus(String owner, String repo, String headSha) throws IOException {
        JsonNode node = exec(authed(url("/repos/" + owner + "/" + repo + "/actions/runs?head_sha=" + headSha + "&per_page=1")).get());
        if (node.path("total_count").asInt(0) == 0) {
            return new WorkflowRunStatus("no_run", null, null);
        }
        JsonNode run = node.path("workflow_runs").get(0);
        return new WorkflowRunStatus(
                run.path("status").asText(),
                run.path("conclusion").asText(""),
                run.path("html_url").asText(""));
    }

    public List<CommitInfo> listCommits(String owner, String repo, String branch, int perPage) throws IOException {
        JsonNode node = exec(authed(url("/repos/" + owner + "/" + repo + "/commits?sha=" + branch + "&per_page=" + perPage)).get());
        List<CommitInfo> commits = new ArrayList<>();
        if (node.isArray()) {
            for (JsonNode c : node) {
                commits.add(new CommitInfo(
                        c.path("sha").asText(),
                        c.path("commit").path("message").asText()));
            }
        }
        return commits;
    }

    public record FileContent(String content, String sha) {}
    public record WorkflowRunStatus(String status, String conclusion, String htmlUrl) {}
    public record CommitInfo(String sha, String message) {}
}
