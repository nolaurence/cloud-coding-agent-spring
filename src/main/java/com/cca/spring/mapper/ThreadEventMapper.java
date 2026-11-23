package com.cca.spring.mapper;

import com.cca.spring.domain.ThreadEvent;
import io.mybatis.mapper.Mapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface ThreadEventMapper extends Mapper<ThreadEvent, Long> {

  @Select("SELECT COALESCE(MAX(sequence_number), 0) FROM thread_events WHERE thread_id = #{threadId}")
  long selectMaxSequence(@Param("threadId") String threadId);

  @Select("SELECT * FROM thread_events WHERE thread_id = #{threadId} ORDER BY sequence_number ASC")
  List<ThreadEvent> selectByThread(@Param("threadId") String threadId);

  @Select("SELECT * FROM thread_events WHERE thread_id = #{threadId} AND sequence_number > #{afterSequence} "
      + "ORDER BY sequence_number ASC LIMIT #{limit}")
  List<ThreadEvent> selectAfter(@Param("threadId") String threadId,
                               @Param("afterSequence") long afterSequence,
                               @Param("limit") int limit);

  @Delete("DELETE FROM thread_events WHERE thread_id = #{threadId}")
  int deleteByThread(@Param("threadId") String threadId);
}
