ALTER TABLE manual_scraping_job
    ADD COLUMN rename_plan TEXT;

ALTER TABLE manual_scraping_job
    ADD COLUMN rename_operation_index INTEGER DEFAULT 0;

ALTER TABLE manual_scraping_job
    ADD COLUMN renamed_directory_count INTEGER DEFAULT 0;
