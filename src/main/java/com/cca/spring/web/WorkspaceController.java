package com.cca.spring.web;

import com.cca.spring.domain.Workspace;
import com.cca.spring.service.FileService;
import com.cca.spring.service.WorkspaceService;
import com.cca.spring.web.dto.ApiDtos.CreateWorkspaceRequest;
import com.cca.spring.web.dto.ApiDtos.FileContentResponse;
import com.cca.spring.web.dto.ApiDtos.FileEntryResponse;
import com.cca.spring.web.dto.ApiDtos.FileWriteRequest;
import com.cca.spring.web.dto.ApiDtos.UpdateWorkspaceRequest;
import com.cca.spring.web.dto.ApiDtos.WorkspaceResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/workspaces")
public class WorkspaceController {

  private final WorkspaceService workspaceService;
  private final FileService fileService;

  public WorkspaceController(WorkspaceService workspaceService, FileService fileService) {
    this.workspaceService = workspaceService;
    this.fileService = fileService;
  }

  @GetMapping
  public List<WorkspaceResponse> list() {
    return workspaceService.list();
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public WorkspaceResponse create(@Valid @RequestBody CreateWorkspaceRequest request) {
    return workspaceService.create(request);
  }

  @GetMapping("/{id}")
  public WorkspaceResponse get(@PathVariable String id) {
    return workspaceService.get(id);
  }

  @PatchMapping("/{id}")
  public WorkspaceResponse update(@PathVariable String id,
                                  @Valid @RequestBody UpdateWorkspaceRequest request) {
    return workspaceService.update(id, request);
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable String id) {
    workspaceService.delete(id);
  }

  @GetMapping("/{id}/files")
  public List<FileEntryResponse> files(@PathVariable String id,
                                       @RequestParam(required = false) String path) {
    Workspace workspace = workspaceService.getEntity(id);
    return fileService.list(workspace, path);
  }

  @GetMapping("/{id}/file")
  public FileContentResponse file(@PathVariable String id,
                                  @RequestParam(required = false) String path) {
    Workspace workspace = workspaceService.getEntity(id);
    return fileService.read(workspace, path);
  }

  @PutMapping("/{id}/file")
  public FileContentResponse writeFile(@PathVariable String id,
                                       @Valid @RequestBody FileWriteRequest request) {
    Workspace workspace = workspaceService.getEntity(id);
    return fileService.write(workspace, request.path(), request.content());
  }
}
