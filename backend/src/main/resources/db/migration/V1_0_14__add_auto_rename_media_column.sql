-- 普通任务执行时是否根据 TMDB 刮削结果重命名 OpenList 媒体，旧任务默认关闭
ALTER TABLE task_config
ADD COLUMN auto_rename_media BOOLEAN NOT NULL DEFAULT 0;
