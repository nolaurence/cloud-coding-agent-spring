package com.cca.spring.service;

import com.cca.spring.web.dto.ApiDtos.WsEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class EventBroadcaster {

  private static final Logger log = LoggerFactory.getLogger(EventBroadcaster.class);

  private final ObjectMapper objectMapper;
  private final ConcurrentHashMap<WebSocketSession, Set<String>> subscriptions = new ConcurrentHashMap<>();

  public EventBroadcaster(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public void subscribe(WebSocketSession session, String threadId) {
    subscriptions.computeIfAbsent(session, key -> ConcurrentHashMap.newKeySet()).add(threadId);
  }

  public void unsubscribe(WebSocketSession session, String threadId) {
    Set<String> threads = subscriptions.get(session);
    if (threads != null) {
      threads.remove(threadId);
    }
  }

  public void unsubscribeAll(WebSocketSession session) {
    subscriptions.remove(session);
  }

  public void broadcast(String threadId, WsEvent event) {
    for (var entry : subscriptions.entrySet()) {
      if (entry.getValue().contains(threadId)) {
        send(entry.getKey(), event);
      }
    }
  }

  public void broadcastAll(WsEvent event) {
    for (WebSocketSession session : subscriptions.keySet()) {
      send(session, event);
    }
  }

  private void send(WebSocketSession session, WsEvent event) {
    if (!session.isOpen()) {
      subscriptions.remove(session);
      return;
    }
    try {
      String payload = objectMapper.writeValueAsString(event);
      synchronized (session) {
        session.sendMessage(new TextMessage(payload));
      }
    } catch (IOException ex) {
      log.warn("WebSocket 发送失败 session={}: {}", session.getId(), ex.getMessage());
      subscriptions.remove(session);
    }
  }
}
