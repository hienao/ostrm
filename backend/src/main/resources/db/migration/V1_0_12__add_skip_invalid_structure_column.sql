-- 跳过目录结构不符合任务媒体库类型的视频，旧任务默认保持原有处理行为
ALTER TABLE task_config
ADD COLUMN skip_invalid_structure BOOLEAN NOT NULL DEFAULT 0;
