package com.cca.spring.mapper;

import com.cca.spring.domain.Workspace;
import io.mybatis.mapper.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface WorkspaceMapper extends Mapper<Workspace, String> {

  @Select("SELECT * FROM workspaces ORDER BY archived ASC, sort_order ASC, created_at ASC")
  List<Workspace> selectAllOrdered();
  @Select("SELECT COUNT(*) FROM workspaces")
  long countAll();
}
