CREATE TABLE IF NOT EXISTS workspaces (
  id VARCHAR(36) NOT NULL PRIMARY KEY,
  name VARCHAR(128) NOT NULL,
  path VARCHAR(1024) NOT NULL,
  description VARCHAR(512),
  sort_order INT NOT NULL DEFAULT 0,
  archived TINYINT NOT NULL DEFAULT 0,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS threads (
  id VARCHAR(36) NOT NULL PRIMARY KEY,
  workspace_id VARCHAR(36) NOT NULL,
  title VARCHAR(255) NOT NULL,
  model VARCHAR(128),
  reasoning_effort VARCHAR(32),
  permission_mode VARCHAR(32) NOT NULL DEFAULT 'request',
  status VARCHAR(32) NOT NULL DEFAULT 'IDLE',
  copilot_session_id VARCHAR(128),
  archived TINYINT NOT NULL DEFAULT 0,
  archived_at TIMESTAMP,
  pinned_at TIMESTAMP,
  last_error CLOB,
  last_message_preview VARCHAR(512),
  message_count INT NOT NULL DEFAULT 0,
  total_input_tokens BIGINT NOT NULL DEFAULT 0,
  total_output_tokens BIGINT NOT NULL DEFAULT 0,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  last_message_at TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_threads_workspace ON threads (workspace_id, archived, updated_at);
CREATE INDEX IF NOT EXISTS idx_threads_status ON threads (status);

CREATE TABLE IF NOT EXISTS thread_messages (
  id VARCHAR(64) NOT NULL PRIMARY KEY,
  thread_id VARCHAR(36) NOT NULL,
  role VARCHAR(16) NOT NULL,
  turn_id VARCHAR(64),
  content CLOB,
  reasoning CLOB,
  status VARCHAR(16) NOT NULL DEFAULT 'complete',
  model VARCHAR(128),
  input_tokens BIGINT NOT NULL DEFAULT 0,
  output_tokens BIGINT NOT NULL DEFAULT 0,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_messages_thread ON thread_messages (thread_id, created_at);
CREATE INDEX IF NOT EXISTS idx_messages_turn ON thread_messages (thread_id, turn_id);

CREATE TABLE IF NOT EXISTS thread_events (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  event_id VARCHAR(64) NOT NULL,
  thread_id VARCHAR(36) NOT NULL,
  sequence_number BIGINT NOT NULL,
  event_type VARCHAR(64) NOT NULL,
  role VARCHAR(16),
  content CLOB,
  payload CLOB,
  tool_call_id VARCHAR(128),
  tool_name VARCHAR(128),
  turn_id VARCHAR(64),
  status VARCHAR(32),
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uk_thread_event UNIQUE (thread_id, event_id),
  CONSTRAINT uk_thread_sequence UNIQUE (thread_id, sequence_number)
);
CREATE INDEX IF NOT EXISTS idx_thread_events_created ON thread_events (thread_id, created_at);

CREATE TABLE IF NOT EXISTS permission_requests (
  id VARCHAR(36) NOT NULL PRIMARY KEY,
  thread_id VARCHAR(36) NOT NULL,
  request_id VARCHAR(128) NOT NULL,
  tool_call_id VARCHAR(128),
  tool_name VARCHAR(128),
  kind VARCHAR(64),
  title VARCHAR(255),
  description CLOB,
  arguments_json CLOB,
  status VARCHAR(24) NOT NULL DEFAULT 'PENDING',
  decision VARCHAR(24),
  feedback CLOB,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  resolved_at TIMESTAMP,
  CONSTRAINT uk_permission_request UNIQUE (thread_id, request_id)
);
CREATE INDEX IF NOT EXISTS idx_permission_status ON permission_requests (status);
CREATE INDEX IF NOT EXISTS idx_permission_thread ON permission_requests (thread_id, created_at);
