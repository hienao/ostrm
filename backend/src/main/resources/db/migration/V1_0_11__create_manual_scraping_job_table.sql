CREATE TABLE manual_scraping_job
(
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    task_id INTEGER NOT NULL,
    directory_path VARCHAR(1000) NOT NULL,
    final_directory_path VARCHAR(1000),
    media_type VARCHAR(20) NOT NULL,
    tmdb_id INTEGER NOT NULL,
    rename_media INTEGER DEFAULT 0,
    status VARCHAR(20) NOT NULL,
    stage VARCHAR(30) NOT NULL,
    progress INTEGER DEFAULT 0,
    message VARCHAR(1000),
    error_message TEXT,
    renamed_file_count INTEGER DEFAULT 0,
    uploaded_files TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_manual_scraping_job_task
    ON manual_scraping_job(task_id, created_at DESC);

CREATE UNIQUE INDEX idx_manual_scraping_job_active_task
    ON manual_scraping_job(task_id)
    WHERE status IN ('PENDING', 'RUNNING');
