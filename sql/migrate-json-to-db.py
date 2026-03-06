#!/usr/bin/env python3
"""
迁移 JSON 配置文件到 Oracle 数据库

使用方法:
    python3 sql/migrate-json-to-db.py

环境变量:
    DB_HOST: 数据库主机 (默认: localhost)
    DB_PORT: 数据库端口 (默认: 1521)
    DB_SID: 数据库 SID (默认: helowin)
    DB_USER: 数据库用户 (默认: flink_user)
    DB_PASSWORD: 数据库密码 (默认: password)
"""

import json
import os
import sys
from pathlib import Path
from datetime import datetime

try:
    import oracledb
except ImportError:
    print("错误: 需要安装 oracledb 库")
    print("安装命令: pip install oracledb")
    sys.exit(1)

# 数据库连接配置
DB_HOST = os.getenv('DB_HOST', 'localhost')
DB_PORT = os.getenv('DB_PORT', '1521')
DB_SID = os.getenv('DB_SID', 'helowin')
DB_USER = os.getenv('DB_USER', 'flink_user')
DB_PASSWORD = os.getenv('DB_PASSWORD', 'password')

# 配置目录
DATASOURCE_DIR = Path('monitor/config/datasources')
TASK_DIR = Path('monitor/config/cdc_tasks')


def connect_db():
    """连接到 Oracle 数据库"""
    # 初始化 thick 模式以支持 Oracle 11g
    try:
        oracledb.init_oracle_client()
    except Exception as e:
        print(f"警告: 无法初始化 Oracle Client: {e}")
        print("尝试使用 thin 模式连接...")
    
    dsn = f"{DB_HOST}:{DB_PORT}/{DB_SID}"
    conn = oracledb.connect(user=DB_USER, password=DB_PASSWORD, dsn=dsn)
    print(f"✓ 已连接到数据库: {DB_USER}@{DB_HOST}:{DB_PORT}/{DB_SID}")
    return conn


def migrate_datasources(conn):
    """迁移数据源配置"""
    if not DATASOURCE_DIR.exists():
        print(f"⚠ 数据源目录不存在: {DATASOURCE_DIR}")
        return 0
    
    cursor = conn.cursor()
    count = 0
    
    for json_file in DATASOURCE_DIR.glob('*.json'):
        try:
            with open(json_file, 'r', encoding='utf-8') as f:
                config = json.load(f)
            
            ds_id = config.get('id', json_file.stem)
            name = config.get('name', 'Unnamed')
            host = config.get('host', 'localhost')
            port = config.get('port', 1521)
            username = config.get('username', '')
            password = config.get('password', '')
            sid = config.get('sid', '')
            description = config.get('description', '')
            
            # 使用 MERGE 语句插入或更新
            sql = """
            MERGE INTO flink_user.cdc_datasources t
            USING (SELECT :1 AS id FROM dual) s
            ON (t.id = s.id)
            WHEN MATCHED THEN
                UPDATE SET name=:2, host=:3, port=:4, username=:5, password=:6, 
                           sid=:7, description=:8, updated_at=CURRENT_TIMESTAMP
            WHEN NOT MATCHED THEN
                INSERT (id, name, host, port, username, password, sid, description, 
                        created_at, updated_at)
                VALUES (:1, :2, :3, :4, :5, :6, :7, :8, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            """
            
            cursor.execute(sql, (ds_id, name, host, port, username, password, sid, description))
            count += 1
            print(f"  ✓ 迁移数据源: {ds_id} ({name})")
            
        except Exception as e:
            print(f"  ✗ 迁移失败 {json_file}: {e}")
    
    conn.commit()
    cursor.close()
    return count


def migrate_tasks(conn):
    """迁移任务配置"""
    if not TASK_DIR.exists():
        print(f"⚠ 任务目录不存在: {TASK_DIR}")
        return 0
    
    cursor = conn.cursor()
    count = 0
    
    for json_file in TASK_DIR.glob('*.json'):
        try:
            with open(json_file, 'r', encoding='utf-8') as f:
                config = json.load(f)
            
            task_id = config.get('id', json_file.stem)
            name = config.get('name', 'Unnamed')
            datasource_id = config.get('datasourceId', '')
            schema_name = config.get('schema', '')
            tables = json.dumps(config.get('tables', []))
            output_path = config.get('outputPath', './output/cdc')
            parallelism = config.get('parallelism', 4)
            split_size = config.get('splitSize', 8096)
            status = config.get('status', 'CREATED')
            flink_job_id = config.get('flinkJobId', None)
            
            # 使用 MERGE 语句插入或更新
            sql = """
            MERGE INTO flink_user.cdc_tasks t
            USING (SELECT :1 AS id FROM dual) s
            ON (t.id = s.id)
            WHEN MATCHED THEN
                UPDATE SET name=:2, datasource_id=:3, schema_name=:4, tables=:5, 
                           output_path=:6, parallelism=:7, split_size=:8, status=:9, 
                           flink_job_id=:10, updated_at=CURRENT_TIMESTAMP
            WHEN NOT MATCHED THEN
                INSERT (id, name, datasource_id, schema_name, tables, output_path, 
                        parallelism, split_size, status, flink_job_id, created_at, updated_at)
                VALUES (:1, :2, :3, :4, :5, :6, :7, :8, :9, :10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            """
            
            cursor.execute(sql, (task_id, name, datasource_id, schema_name, tables, 
                                output_path, parallelism, split_size, status, flink_job_id))
            count += 1
            print(f"  ✓ 迁移任务: {task_id} ({name})")
            
        except Exception as e:
            print(f"  ✗ 迁移失败 {json_file}: {e}")
    
    conn.commit()
    cursor.close()
    return count


def main():
    """主函数"""
    print("=" * 60)
    print("迁移 JSON 配置到 Oracle 数据库")
    print("=" * 60)
    
    try:
        # 连接数据库
        conn = connect_db()
        
        # 迁移数据源
        print("\n1. 迁移数据源配置...")
        ds_count = migrate_datasources(conn)
        print(f"   共迁移 {ds_count} 个数据源")
        
        # 迁移任务
        print("\n2. 迁移任务配置...")
        task_count = migrate_tasks(conn)
        print(f"   共迁移 {task_count} 个任务")
        
        conn.close()
        
        print("\n" + "=" * 60)
        print(f"✓ 迁移完成! 数据源: {ds_count}, 任务: {task_count}")
        print("=" * 60)
        
    except Exception as e:
        print(f"\n✗ 迁移失败: {e}")
        sys.exit(1)


if __name__ == '__main__':
    main()
