package com.cca.spring.mapper;

import com.cca.spring.domain.PermissionRequestEntity;
import io.mybatis.mapper.Mapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Optional;

public interface PermissionRequestMapper extends Mapper<PermissionRequestEntity, String> {

  @Select("SELECT * FROM permission_requests WHERE thread_id = #{threadId} AND status = 'PENDING' "
      + "ORDER BY created_at ASC")
  List<PermissionRequestEntity> selectPendingByThread(@Param("threadId") String threadId);

  @Select("SELECT * FROM permission_requests WHERE id = #{id}")
  Optional<PermissionRequestEntity> selectById(@Param("id") String id);

  @Select("SELECT * FROM permission_requests WHERE thread_id = #{threadId} ORDER BY created_at ASC")
  List<PermissionRequestEntity> selectByThread(@Param("threadId") String threadId);

  @Select("SELECT * FROM permission_requests WHERE status = 'PENDING' ORDER BY created_at ASC")
  List<PermissionRequestEntity> selectAllPending();

  @Delete("DELETE FROM permission_requests WHERE thread_id = #{threadId}")
  int deleteByThread(@Param("threadId") String threadId);
}
