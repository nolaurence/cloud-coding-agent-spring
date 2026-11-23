package com.cca.spring.mapper;

import com.cca.spring.domain.ThreadMessage;
import io.mybatis.mapper.Mapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface ThreadMessageMapper extends Mapper<ThreadMessage, String> {

  @Select("SELECT * FROM thread_messages WHERE thread_id = #{threadId} ORDER BY created_at ASC, id ASC")
  List<ThreadMessage> selectByThread(@Param("threadId") String threadId);

  @Delete("DELETE FROM thread_messages WHERE thread_id = #{threadId}")
  int deleteByThread(@Param("threadId") String threadId);
}
