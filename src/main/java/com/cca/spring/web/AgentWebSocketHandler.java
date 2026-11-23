package com.cca.spring.web;

import com.cca.spring.service.EventBroadcaster;
import com.cca.spring.service.ThreadService;
import com.cca.spring.service.TimelineService;
import com.cca.spring.web.dto.ApiDtos.WsEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;

@Component
public class AgentWebSocketHandler extends TextWebSocketHandler {

  private static final Logger log = LoggerFactory.getLogger(AgentWebSocketHandler.class);

  private final EventBroadcaster broadcaster;
  private final ThreadService threadService;
  private final TimelineService timelineService;
  private final ObjectMapper objectMapper;

  public AgentWebSocketHandler(EventBroadcaster broadcaster,
                               ThreadService threadService,
                               TimelineService timelineService,
                               ObjectMapper objectMapper) {
    this.broadcaster = broadcaster;
    this.threadService = threadService;
    this.timelineService = timelineService;
    this.objectMapper = objectMapper;
  }

  @Override
  public void afterConnectionEstablished(WebSocketSession session) throws Exception {
    send(session, WsEvent.of("connection", null, "open"));
  }

  @Override
  protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
    JsonNode root;
    try {
      root = objectMapper.readTree(message.getPayload());
    } catch (Exception ex) {
      send(session, WsEvent.of("error", null, "消息格式错误"));
      return;
    }
    String type = root.path("type").asText("");
    String threadId = root.path("threadId").asText("");
    switch (type) {
      case "ping" -> send(session, WsEvent.of("pong", null, "ok"));
      case "subscribe" -> {
        if (threadId.isBlank()) {
          send(session, WsEvent.of("error", null, "threadId 不能为空"));
          return;
        }
        try {
          threadService.getEntity(threadId);
          broadcaster.subscribe(session, threadId);
          send(session, WsEvent.of("thread.snapshot", threadId, timelineService.detail(threadId)));
        } catch (Exception ex) {
          send(session, WsEvent.of("error", threadId, ex.getMessage()));
        }
      }
      case "unsubscribe" -> broadcaster.unsubscribe(session, threadId);
      default -> send(session, WsEvent.of("error", null, "未知的 WebSocket 消息类型: " + type));
    }
  }

  @Override
  public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
    broadcaster.unsubscribeAll(session);
  }

  @Override
  public void handleTransportError(WebSocketSession session, Throwable exception) {
    log.warn("WebSocket 传输错误 session={}: {}", session.getId(), exception.getMessage());
  }

  private void send(WebSocketSession session, WsEvent event) throws IOException {
    if (session.isOpen()) {
      synchronized (session) {
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(event)));
      }
    }
  }
}
