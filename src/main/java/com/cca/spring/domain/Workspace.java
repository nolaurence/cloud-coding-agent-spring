package com.cca.spring.domain;

import io.mybatis.provider.Entity;

import java.time.Instant;

@Entity.Table("workspaces")
public class Workspace {

  @Entity.Column(id = true)
  private String id;

  @Entity.Column("name")
  private String name;

  @Entity.Column("path")
  private String path;

  @Entity.Column("description")
  private String description;

  @Entity.Column("sort_order")
  private Integer sortOrder;

  @Entity.Column("archived")
  private Boolean archived;

  @Entity.Column("created_at")
  private Instant createdAt;

  @Entity.Column("updated_at")
  private Instant updatedAt;

  public String getId() { return id; }
  public void setId(String id) { this.id = id; }
  public String getName() { return name; }
  public void setName(String name) { this.name = name; }
  public String getPath() { return path; }
  public void setPath(String path) { this.path = path; }
  public String getDescription() { return description; }
  public void setDescription(String description) { this.description = description; }
  public Integer getSortOrder() { return sortOrder; }
  public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
  public Boolean getArchived() { return archived; }
  public void setArchived(Boolean archived) { this.archived = archived; }
  public Instant getCreatedAt() { return createdAt; }
  public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
  public Instant getUpdatedAt() { return updatedAt; }
  public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
