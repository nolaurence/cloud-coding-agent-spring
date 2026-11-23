CREATE TABLE IF NOT EXISTS workspaces (
  id VARCHAR(36) NOT NULL,
  name VARCHAR(128) NOT NULL,
  path VARCHAR(1024) NOT NULL,
  description VARCHAR(512) NULL,
  sort_order INT NOT NULL DEFAULT 0,
  archived TINYINT(1) NOT NULL DEFAULT 0,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS threads (
  id VARCHAR(36) NOT NULL,
  workspace_id VARCHAR(36) NOT NULL,
  title VARCHAR(255) NOT NULL,
  model VARCHAR(128) NULL,
  reasoning_effort VARCHAR(32) NULL,
  permission_mode VARCHAR(32) NOT NULL DEFAULT 'request',
  status VARCHAR(32) NOT NULL DEFAULT 'IDLE',
  copilot_session_id VARCHAR(128) NULL,
  archived TINYINT(1) NOT NULL DEFAULT 0,
  archived_at TIMESTAMP NULL,
  pinned_at TIMESTAMP NULL,
  last_error TEXT NULL,
  last_message_preview VARCHAR(512) NULL,
  message_count INT NOT NULL DEFAULT 0,
  total_input_tokens BIGINT NOT NULL DEFAULT 0,
  total_output_tokens BIGINT NOT NULL DEFAULT 0,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  last_message_at TIMESTAMP NULL,
  PRIMARY KEY (id),
  KEY idx_threads_workspace (workspace_id, archived, updated_at),
  KEY idx_threads_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS thread_messages (
  id VARCHAR(64) NOT NULL,
  thread_id VARCHAR(36) NOT NULL,
  role VARCHAR(16) NOT NULL,
  turn_id VARCHAR(64) NULL,
  content LONGTEXT NULL,
  reasoning LONGTEXT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'complete',
  model VARCHAR(128) NULL,
  input_tokens BIGINT NOT NULL DEFAULT 0,
  output_tokens BIGINT NOT NULL DEFAULT 0,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_messages_thread (thread_id, created_at),
  KEY idx_messages_turn (thread_id, turn_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS thread_events (
  id BIGINT NOT NULL AUTO_INCREMENT,
  event_id VARCHAR(64) NOT NULL,
  thread_id VARCHAR(36) NOT NULL,
  sequence_number BIGINT NOT NULL,
  event_type VARCHAR(64) NOT NULL,
  role VARCHAR(16) NULL,
  content LONGTEXT NULL,
  payload LONGTEXT NULL,
  tool_call_id VARCHAR(128) NULL,
  tool_name VARCHAR(128) NULL,
  turn_id VARCHAR(64) NULL,
  status VARCHAR(32) NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_thread_event (thread_id, event_id),
  UNIQUE KEY uk_thread_sequence (thread_id, sequence_number),
  KEY idx_thread_events_created (thread_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS permission_requests (
  id VARCHAR(36) NOT NULL,
  thread_id VARCHAR(36) NOT NULL,
  request_id VARCHAR(128) NOT NULL,
  tool_call_id VARCHAR(128) NULL,
  tool_name VARCHAR(128) NULL,
  kind VARCHAR(64) NULL,
  title VARCHAR(255) NULL,
  description TEXT NULL,
  arguments_json LONGTEXT NULL,
  status VARCHAR(24) NOT NULL DEFAULT 'PENDING',
  decision VARCHAR(24) NULL,
  feedback TEXT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  resolved_at TIMESTAMP NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_permission_request (thread_id, request_id),
  KEY idx_permission_status (status),
  KEY idx_permission_thread (thread_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
