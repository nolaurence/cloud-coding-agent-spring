package com.cca.spring.web;

import com.cca.spring.service.ChatService;
import com.cca.spring.service.TimelineService;
import com.cca.spring.web.dto.ApiDtos.MessageResponse;
import com.cca.spring.web.dto.ApiDtos.PermissionResponse;
import com.cca.spring.web.dto.ApiDtos.ResolvePermissionRequest;
import com.cca.spring.web.dto.ApiDtos.SendMessageRequest;
import com.cca.spring.web.dto.ApiDtos.ThreadDetailResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class ChatController {

  private final ChatService chatService;
  private final TimelineService timelineService;

  public ChatController(ChatService chatService, TimelineService timelineService) {
    this.chatService = chatService;
    this.timelineService = timelineService;
  }

  @GetMapping("/threads/{id}/timeline")
  public ThreadDetailResponse timeline(@PathVariable String id) {
    return timelineService.detail(id);
  }

  @PostMapping("/threads/{id}/messages")
  public MessageResponse send(@PathVariable String id,
                              @Valid @RequestBody SendMessageRequest request) {
    return chatService.send(id, request);
  }

  @PostMapping("/threads/{id}/abort")
  public void abort(@PathVariable String id) {
    chatService.abort(id);
  }

  @GetMapping("/threads/{id}/permissions")
  public List<PermissionResponse> permissions(@PathVariable String id) {
    return chatService.pendingPermissions(id);
  }

  @PostMapping("/permissions/{id}/resolve")
  public PermissionResponse resolve(@PathVariable String id,
                                    @Valid @RequestBody ResolvePermissionRequest request) {
    return chatService.resolvePermission(id, request);
  }
}
