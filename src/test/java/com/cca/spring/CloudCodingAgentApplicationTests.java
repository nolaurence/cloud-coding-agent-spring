package com.cca.spring;

import com.cca.spring.domain.PermissionRequestEntity;
import com.cca.spring.domain.ThreadEvent;
import com.cca.spring.domain.ThreadMessage;
import com.cca.spring.mapper.PermissionRequestMapper;
import com.cca.spring.mapper.ThreadEventMapper;
import com.cca.spring.mapper.ThreadMessageMapper;
import com.cca.spring.web.dto.ApiDtos.CreateThreadRequest;
import com.cca.spring.web.dto.ApiDtos.CreateWorkspaceRequest;
import com.cca.spring.web.dto.ApiDtos.FileWriteRequest;
import com.cca.spring.web.dto.ApiDtos.UpdateThreadRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class CloudCodingAgentApplicationTests {

  @Autowired
  private TestRestTemplate restTemplate;

  @Autowired
  private ObjectMapper objectMapper;

  @Autowired
  private ThreadMessageMapper messageMapper;

  @Autowired
  private ThreadEventMapper eventMapper;

  @Autowired
  private PermissionRequestMapper permissionMapper;

  @Test
  void contextLoads() {
  }

  @Test
  void frontendAssetsAreServed() {
    ResponseEntity<String> indexResponse = restTemplate.getForEntity("/", String.class);
    assertThat(indexResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(indexResponse.getBody()).contains("/app.js");

    ResponseEntity<String> appJsResponse = restTemplate.getForEntity("/app.js", String.class);
    assertThat(appJsResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(appJsResponse.getBody()).contains("function");

    ResponseEntity<String> configResponse = restTemplate.getForEntity("/api/config", String.class);
    assertThat(configResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(configResponse.getBody()).contains("securityEnabled");

    ResponseEntity<String> notFoundResponse = restTemplate.getForEntity("/does-not-exist", String.class);
    assertThat(notFoundResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void workspaceCrud() throws Exception {
    ResponseEntity<JsonNode> createResponse = createWorkspace();
    assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    JsonNode workspace = createResponse.getBody();
    String workspaceId = workspace.get("id").asText();

    assertThat(workspace.get("name").asText()).startsWith("测试工作区");
    assertThat(workspace.get("path").asText()).isNotBlank();

    ResponseEntity<JsonNode> listResponse = restTemplate.getForEntity("/api/workspaces", JsonNode.class);
    assertThat(listResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(listResponse.getBody().isArray()).isTrue();

    ResponseEntity<JsonNode> updateResponse = exchange(
        HttpMethod.PATCH, "/api/workspaces/" + workspaceId, Map.of("name", "重命名工作区"));
    assertThat(updateResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(updateResponse.getBody().get("name").asText()).isEqualTo("重命名工作区");

    ResponseEntity<Void> deleteResponse = restTemplate.exchange(
        "/api/workspaces/" + workspaceId, HttpMethod.DELETE, HttpEntity.EMPTY, Void.class);
    assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

    ResponseEntity<JsonNode> getResponse = restTemplate.getForEntity(
        "/api/workspaces/" + workspaceId, JsonNode.class);
    assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void threadTimelineAndPermissions() throws Exception {
    JsonNode workspace = createWorkspace().getBody();
    String workspaceId = workspace.get("id").asText();

    ResponseEntity<JsonNode> threadResponse = exchange(
        HttpMethod.POST,
        "/api/workspaces/" + workspaceId + "/threads",
        new CreateThreadRequest("测试会话", "gpt-5", null, "request"));
    assertThat(threadResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(threadResponse.getBody().get("title").asText()).isEqualTo("测试会话");
    String threadId = threadResponse.getBody().get("id").asText();

    ResponseEntity<JsonNode> updateResponse = exchange(
        HttpMethod.PATCH,
        "/api/threads/" + threadId,
        new UpdateThreadRequest("更新后的会话", "gpt-5", "medium", "auto", null, true));
    assertThat(updateResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(updateResponse.getBody().get("title").asText()).isEqualTo("更新后的会话");
    assertThat(updateResponse.getBody().get("permissionMode").asText()).isEqualTo("auto");
    assertThat(updateResponse.getBody().get("pinnedAt").isNull()).isFalse();

    insertThreadData(threadId);

    ResponseEntity<JsonNode> timelineResponse = restTemplate.getForEntity(
        "/api/threads/" + threadId + "/timeline", JsonNode.class);
    assertThat(timelineResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode timeline = timelineResponse.getBody();
    assertThat(timeline.get("messages")).hasSize(2);
    assertThat(timeline.get("activities")).hasSize(1);
    assertThat(timeline.get("pendingPermissions")).hasSize(1);
    assertThat(timeline.get("messages").get(0).get("role").asText()).isEqualTo("user");
    assertThat(timeline.get("messages").get(1).get("role").asText()).isEqualTo("assistant");
    assertThat(timeline.get("activities").get(0).get("status").asText()).isEqualTo("complete");
    assertThat(timeline.get("pendingPermissions").get(0).get("status").asText()).isEqualTo("PENDING");

    ResponseEntity<JsonNode> permissionsResponse = restTemplate.getForEntity(
        "/api/threads/" + threadId + "/permissions", JsonNode.class);
    assertThat(permissionsResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(permissionsResponse.getBody()).hasSize(1);
    assertThat(permissionsResponse.getBody().get(0).get("status").asText()).isEqualTo("PENDING");

    ResponseEntity<Void> deleteThread = restTemplate.exchange(
        "/api/threads/" + threadId, HttpMethod.DELETE, HttpEntity.EMPTY, Void.class);
    assertThat(deleteThread.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    ResponseEntity<JsonNode> getThread = restTemplate.getForEntity(
        "/api/threads/" + threadId, JsonNode.class);
    assertThat(getThread.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

    ResponseEntity<Void> deleteWorkspace = restTemplate.exchange(
        "/api/workspaces/" + workspaceId, HttpMethod.DELETE, HttpEntity.EMPTY, Void.class);
    assertThat(deleteWorkspace.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
  }

  @Test
  void fileApiAndPathProtection() throws Exception {
    JsonNode workspace = createWorkspace().getBody();
    String workspaceId = workspace.get("id").asText();

    ResponseEntity<JsonNode> writeResponse = exchange(
        HttpMethod.PUT,
        "/api/workspaces/" + workspaceId + "/file",
        new FileWriteRequest("src/demo/Demo.java", "class Demo {}"));
    assertThat(writeResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(writeResponse.getBody().get("path").asText()).isEqualTo("src/demo/Demo.java");
    assertThat(writeResponse.getBody().get("content").asText()).isEqualTo("class Demo {}");

    ResponseEntity<JsonNode> readResponse = restTemplate.getForEntity(
        "/api/workspaces/" + workspaceId + "/file?path=src/demo/Demo.java", JsonNode.class);
    assertThat(readResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(readResponse.getBody().get("content").asText()).isEqualTo("class Demo {}");

    String encodedTraversal = "../outside.txt";
    ResponseEntity<JsonNode> traversalResponse = restTemplate.getForEntity(
        "/api/workspaces/" + workspaceId + "/file?path=" + encodedTraversal, JsonNode.class);
    assertThat(traversalResponse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

    String encodedAbsolute = "/etc/passwd";
    ResponseEntity<JsonNode> absoluteResponse = restTemplate.getForEntity(
        "/api/workspaces/" + workspaceId + "/file?path=" + encodedAbsolute, JsonNode.class);
    assertThat(absoluteResponse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

    ResponseEntity<JsonNode> filesResponse = restTemplate.getForEntity(
        "/api/workspaces/" + workspaceId + "/files", JsonNode.class);
    assertThat(filesResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(filesResponse.getBody().isArray()).isTrue();
    assertThat(filesResponse.getBody().size()).isGreaterThan(0);

    ResponseEntity<Void> deleteWorkspace = restTemplate.exchange(
        "/api/workspaces/" + workspaceId, HttpMethod.DELETE, HttpEntity.EMPTY, Void.class);
    assertThat(deleteWorkspace.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
  }

  private ResponseEntity<JsonNode> createWorkspace() throws Exception {
    String suffix = UUID.randomUUID().toString().substring(0, 8);
    return exchange(
        HttpMethod.POST,
        "/api/workspaces",
        new CreateWorkspaceRequest("测试工作区-" + suffix, "test-" + suffix, "自动化测试"));
  }

  private ResponseEntity<JsonNode> exchange(HttpMethod method, String path, Object body) throws Exception {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    String json = body instanceof String ? (String) body : objectMapper.writeValueAsString(body);
    HttpEntity<String> entity = new HttpEntity<>(json, headers);
    return restTemplate.exchange(path, method, entity, JsonNode.class);
  }

  private void insertThreadData(String threadId) {
    Instant now = Instant.now();
    String turnId = UUID.randomUUID().toString();

    ThreadMessage user = new ThreadMessage();
    user.setId(turnId);
    user.setThreadId(threadId);
    user.setRole("user");
    user.setTurnId(turnId);
    user.setContent("你好，Copilot");
    user.setStatus("complete");
    user.setInputTokens(0L);
    user.setOutputTokens(0L);
    user.setCreatedAt(now);
    messageMapper.insert(user);

    ThreadMessage assistant = new ThreadMessage();
    assistant.setId(UUID.randomUUID().toString());
    assistant.setThreadId(threadId);
    assistant.setRole("assistant");
    assistant.setTurnId(turnId);
    assistant.setContent("你好！我是 Copilot。");
    assistant.setStatus("complete");
    assistant.setModel("gpt-5");
    assistant.setInputTokens(10L);
    assistant.setOutputTokens(20L);
    assistant.setCreatedAt(now.plusMillis(1));
    messageMapper.insert(assistant);

    ThreadEvent start = new ThreadEvent();
    start.setEventId(UUID.randomUUID().toString());
    start.setThreadId(threadId);
    start.setSequenceNumber(1L);
    start.setEventType("tool.start");
    start.setRole("tool");
    start.setContent("{\"path\":\"README.md\"}");
    start.setToolCallId("tool-1");
    start.setToolName("read_file");
    start.setTurnId(turnId);
    start.setStatus("running");
    start.setCreatedAt(now.plusMillis(2));
    eventMapper.insert(start);

    ThreadEvent complete = new ThreadEvent();
    complete.setEventId(UUID.randomUUID().toString());
    complete.setThreadId(threadId);
    complete.setSequenceNumber(2L);
    complete.setEventType("tool.complete");
    complete.setRole("tool");
    complete.setContent("# Hello");
    complete.setToolCallId("tool-1");
    complete.setToolName("read_file");
    complete.setTurnId(turnId);
    complete.setStatus("complete");
    complete.setCreatedAt(now.plusMillis(3));
    eventMapper.insert(complete);

    PermissionRequestEntity permission = new PermissionRequestEntity();
    permission.setId(UUID.randomUUID().toString());
    permission.setThreadId(threadId);
    permission.setRequestId(UUID.randomUUID().toString());
    permission.setToolCallId("tool-2");
    permission.setToolName("write_file");
    permission.setKind("file_write");
    permission.setTitle("写入文件");
    permission.setDescription("需要写入 README.md");
    permission.setArgumentsJson("{\"path\":\"README.md\"}");
    permission.setStatus("PENDING");
    permission.setCreatedAt(now.plusMillis(4));
    permissionMapper.insert(permission);
  }
}
