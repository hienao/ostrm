-- OpenList 文件系统 API 每分钟最大调用次数，0 表示不限制
ALTER TABLE openlist_config
ADD COLUMN fs_api_qpm_limit INTEGER NOT NULL DEFAULT 0;
