package com.cca.spring.web;

import com.cca.spring.service.CopilotSessionManager;
import com.cca.spring.service.ThreadService;
import com.cca.spring.web.dto.ApiDtos.CreateThreadRequest;
import com.cca.spring.web.dto.ApiDtos.ThreadResponse;
import com.cca.spring.web.dto.ApiDtos.UpdateThreadRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class ThreadController {

  private final ThreadService threadService;
  private final CopilotSessionManager sessionManager;

  public ThreadController(ThreadService threadService, CopilotSessionManager sessionManager) {
    this.threadService = threadService;
    this.sessionManager = sessionManager;
  }

  @GetMapping("/workspaces/{workspaceId}/threads")
  public List<ThreadResponse> list(@PathVariable String workspaceId,
                                   @RequestParam(defaultValue = "false") boolean includeArchived) {
    return threadService.list(workspaceId, includeArchived);
  }

  @PostMapping("/workspaces/{workspaceId}/threads")
  @ResponseStatus(HttpStatus.CREATED)
  public ThreadResponse create(@PathVariable String workspaceId,
                               @Valid @RequestBody CreateThreadRequest request) {
    return threadService.create(workspaceId, request);
  }

  @GetMapping("/threads/{id}")
  public ThreadResponse get(@PathVariable String id) {
    return threadService.get(id);
  }

  @PatchMapping("/threads/{id}")
  public ThreadResponse update(@PathVariable String id,
                               @Valid @RequestBody UpdateThreadRequest request) {
    return threadService.update(id, request);
  }

  @DeleteMapping("/threads/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable String id) {
    sessionManager.close(id);
    threadService.delete(id);
  }
}
