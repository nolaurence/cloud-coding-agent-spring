package com.cca.spring.mapper;

import com.cca.spring.domain.Thread;
import io.mybatis.mapper.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.Instant;
import java.util.List;

public interface ThreadMapper extends Mapper<Thread, String> {

  @Select("SELECT * FROM threads WHERE workspace_id = #{workspaceId} AND archived = #{archived} "
      + "ORDER BY pinned_at DESC, updated_at DESC")
  List<Thread> selectByWorkspace(@Param("workspaceId") String workspaceId,
                                 @Param("archived") boolean archived);

  @Select("SELECT * FROM threads WHERE archived = 1 AND archived_at IS NOT NULL AND archived_at < #{before}")
  List<Thread> selectArchivedBefore(@Param("before") Instant before);

  @Select("SELECT * FROM threads WHERE status = 'RUNNING' ORDER BY updated_at ASC")
  List<Thread> selectRunning();

  @Select("SELECT COUNT(*) FROM threads WHERE workspace_id = #{workspaceId}")
  long countByWorkspace(@Param("workspaceId") String workspaceId);
}
