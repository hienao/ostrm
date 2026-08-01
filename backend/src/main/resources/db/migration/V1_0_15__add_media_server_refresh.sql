-- Emby/Jellyfin 媒体服务器配置
CREATE TABLE media_server_config
(
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name VARCHAR(200) NOT NULL UNIQUE,
    server_type VARCHAR(20) NOT NULL,
    api_base_url VARCHAR(500) NOT NULL,
    api_key VARCHAR(1000) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT 1,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_media_server_config_active ON media_server_config(is_active);

CREATE TRIGGER update_media_server_config_updated_at
    AFTER UPDATE ON media_server_config
    FOR EACH ROW
BEGIN
    UPDATE media_server_config SET updated_at = CURRENT_TIMESTAMP WHERE id = NEW.id;
END;

-- 任务可选择不刷新、全部刷新或指定媒体库精确刷新
ALTER TABLE task_config ADD COLUMN media_server_config_id INTEGER;
ALTER TABLE task_config ADD COLUMN media_refresh_scope VARCHAR(20) NOT NULL DEFAULT 'NONE';
ALTER TABLE task_config ADD COLUMN media_library_id VARCHAR(200);
ALTER TABLE task_config ADD COLUMN media_library_name VARCHAR(500);

CREATE INDEX idx_task_config_media_server ON task_config(media_server_config_id);
