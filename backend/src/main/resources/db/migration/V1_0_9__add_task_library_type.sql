-- 任务媒体库类型：旧任务保持自动识别，新任务可选择 movie/tv/anime。
ALTER TABLE task_config
ADD COLUMN library_type VARCHAR(20) NOT NULL DEFAULT 'auto';

