-- CDC 文件映射表 — 将文件 ID 映射到实际文件路径
-- 前端只看到 fileId，永远不接触真实路径。

CREATE TABLE finance_user.cdc_files (
    id VARCHAR2(64) PRIMARY KEY,
    file_path VARCHAR2(1024) NOT NULL,
    file_name VARCHAR2(512),
    table_name VARCHAR2(256),
    file_size NUMBER DEFAULT 0,
    line_count NUMBER DEFAULT 0,
    last_modified TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
