package com.cca.spring.config;

import com.cca.spring.web.AgentWebSocketHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;

import java.util.Map;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

  private final AppProperties properties;
  private final AgentWebSocketHandler handler;

  public WebSocketConfig(AppProperties properties, AgentWebSocketHandler handler) {
    this.properties = properties;
    this.handler = handler;
  }

  @Override
  public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
    registry.addHandler(handler, "/ws")
        .addInterceptors(new HandshakeInterceptor() {
          @Override
          public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                         WebSocketHandler wsHandler, Map<String, Object> attributes) {
            if (!properties.getSecurity().isEnabled()) {
              return true;
            }
            String expected = properties.getSecurity().getApiKey();
            if (expected == null || expected.isBlank()) {
              return true;
            }
            String query = request.getURI().getQuery();
            if (query == null) {
              return false;
            }
            for (String pair : query.split("&")) {
              int index = pair.indexOf('=');
              if (index > 0 && "token".equals(pair.substring(0, index))
                  && expected.equals(pair.substring(index + 1))) {
                return true;
              }
            }
            return false;
          }

          @Override
          public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                     WebSocketHandler wsHandler, Exception exception) {
          }
        })
        .setAllowedOriginPatterns("*");
  }

  @Bean
  public ServletServerContainerFactoryBean websocketContainer() {
    ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
    container.setMaxTextMessageBufferSize(2 * 1024 * 1024);
    container.setMaxBinaryMessageBufferSize(2 * 1024 * 1024);
    return container;
  }
}
