package com.cca.spring.service;

import com.cca.spring.config.AppProperties;
import com.cca.spring.domain.Workspace;
import com.cca.spring.mapper.ThreadMapper;
import com.cca.spring.mapper.WorkspaceMapper;
import com.cca.spring.web.dto.ApiDtos.CreateWorkspaceRequest;
import com.cca.spring.web.dto.ApiDtos.UpdateWorkspaceRequest;
import com.cca.spring.web.dto.ApiDtos.WorkspaceResponse;
import com.cca.spring.web.exception.BadRequestException;
import com.cca.spring.web.exception.ConflictException;
import com.cca.spring.web.exception.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class WorkspaceService {

  private static final Logger log = LoggerFactory.getLogger(WorkspaceService.class);

  private final AppProperties properties;
  private final WorkspaceMapper workspaceMapper;
  private final ThreadMapper threadMapper;

  public WorkspaceService(AppProperties properties, WorkspaceMapper workspaceMapper, ThreadMapper threadMapper) {
    this.properties = properties;
    this.workspaceMapper = workspaceMapper;
    this.threadMapper = threadMapper;
  }

  @EventListener(ApplicationReadyEvent.class)
  public void bootstrap() {
    try {
      Files.createDirectories(Paths.get(properties.getDataDir()).toAbsolutePath().normalize());
      Files.createDirectories(workspaceRoot());
      if (properties.isBootstrapDefaultWorkspace() && workspaceMapper.countAll() == 0) {
        Workspace workspace = new Workspace();
        workspace.setId(UUID.randomUUID().toString());
        workspace.setName("默认工作区");
        workspace.setPath(workspaceRoot().resolve("default").toString());
        workspace.setDescription("自动创建的默认工作区");
        workspace.setSortOrder(0);
        workspace.setArchived(false);
        workspace.setCreatedAt(Instant.now());
        workspace.setUpdatedAt(Instant.now());
        Files.createDirectories(Paths.get(workspace.getPath()));
        workspaceMapper.insert(workspace);
        log.info("已创建默认工作区: {}", workspace.getPath());
      }
    } catch (IOException ex) {
      throw new IllegalStateException("无法创建数据目录或工作区目录", ex);
    }
  }

  public List<WorkspaceResponse> list() {
    return workspaceMapper.selectAllOrdered().stream().map(this::toResponse).toList();
  }

  public Workspace getEntity(String id) {
    return workspaceMapper.selectByPrimaryKey(id)
        .orElseThrow(() -> new NotFoundException("工作区不存在: " + id));
  }

  public WorkspaceResponse get(String id) {
    return toResponse(getEntity(id));
  }

  @Transactional
  public WorkspaceResponse create(CreateWorkspaceRequest request) {
    if (!StringUtils.hasText(request.name())) {
      throw new BadRequestException("工作区名称不能为空");
    }
    Path root = workspaceRoot();
    Path path = resolvePath(request.path(), request.name(), root);
    try {
      Files.createDirectories(path);
    } catch (IOException ex) {
      throw new BadRequestException("无法创建工作区目录: " + path);
    }
    Workspace workspace = new Workspace();
    workspace.setId(UUID.randomUUID().toString());
    workspace.setName(request.name().trim());
    workspace.setPath(path.toString());
    workspace.setDescription(request.description());
    workspace.setSortOrder((int) workspaceMapper.countAll());
    workspace.setArchived(false);
    workspace.setCreatedAt(Instant.now());
    workspace.setUpdatedAt(Instant.now());
    workspaceMapper.insert(workspace);
    return toResponse(workspace);
  }

  @Transactional
  public WorkspaceResponse update(String id, UpdateWorkspaceRequest request) {
    Workspace workspace = getEntity(id);
    if (request.name() != null) {
      if (!StringUtils.hasText(request.name())) {
        throw new BadRequestException("工作区名称不能为空");
      }
      workspace.setName(request.name().trim());
    }
    if (request.description() != null) {
      workspace.setDescription(request.description());
    }
    if (request.archived() != null) {
      workspace.setArchived(request.archived());
    }
    if (request.sortOrder() != null) {
      workspace.setSortOrder(request.sortOrder());
    }
    workspace.setUpdatedAt(Instant.now());
    workspaceMapper.updateByPrimaryKeySelective(workspace);
    return toResponse(workspace);
  }

  @Transactional
  public void delete(String id) {
    Workspace workspace = getEntity(id);
    if (threadMapper.countByWorkspace(id) > 0) {
      throw new ConflictException("工作区仍有会话，请先删除或归档会话");
    }
    workspaceMapper.deleteByPrimaryKey(workspace.getId());
  }

  private Path workspaceRoot() {
    return Paths.get(properties.getWorkspaceRoot()).toAbsolutePath().normalize();
  }

  private Path resolvePath(String requested, String name, Path root) {
    Path candidate;
    if (!StringUtils.hasText(requested)) {
      String slug = name.trim().toLowerCase(Locale.ROOT)
          .replaceAll("[^a-z0-9\\u4e00-\\u9fa5]+", "-")
          .replaceAll("(^-|-$)", "");
      if (!StringUtils.hasText(slug)) {
        slug = UUID.randomUUID().toString().substring(0, 8);
      }
      candidate = root.resolve(slug);
    } else {
      candidate = Paths.get(requested);
      if (!candidate.isAbsolute()) {
        candidate = root.resolve(candidate);
      }
    }
    candidate = candidate.toAbsolutePath().normalize();
    if (!candidate.startsWith(root)) {
      throw new BadRequestException("工作区路径必须位于 workspace-root 下");
    }
    return candidate;
  }

  public WorkspaceResponse toResponse(Workspace workspace) {
    return new WorkspaceResponse(
        workspace.getId(),
        workspace.getName(),
        workspace.getPath(),
        workspace.getDescription(),
        workspace.getSortOrder(),
        Boolean.TRUE.equals(workspace.getArchived()),
        workspace.getCreatedAt(),
        workspace.getUpdatedAt());
  }
}
