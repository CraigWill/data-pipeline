#!/usr/bin/env python3
"""
修复 log_mining_flush 表位置
将表从 finance_user schema 移动到 flink_user schema
"""

import cx_Oracle
import sys

# 配置
ORACLE_HOST = "localhost"
ORACLE_PORT = 1521
ORACLE_SID = "helowin"
ORACLE_USER = "system"
ORACLE_PASSWORD = "password"
FLINK_USER = "flink_user"
FLINK_PASSWORD = "flink_password"

def main():
    print("=" * 50)
    print("修复 log_mining_flush 表位置")
    print("=" * 50)
    print(f"Oracle: {ORACLE_USER}@{ORACLE_HOST}:{ORACLE_PORT}/{ORACLE_SID}")
    print()

    # 连接数据库
    dsn = cx_Oracle.makedsn(ORACLE_HOST, ORACLE_PORT, sid=ORACLE_SID)
    
    try:
        conn = cx_Oracle.connect(ORACLE_USER, ORACLE_PASSWORD, dsn)
        cursor = conn.cursor()
        print("✓ 数据库连接成功")
        
        # 1. 创建 flink_user 用户（如果不存在）
        print("\n1. 检查 flink_user 用户...")
        cursor.execute("""
            SELECT COUNT(*) FROM dba_users WHERE username = 'FLINK_USER'
        """)
        count = cursor.fetchone()[0]
        
        if count == 0:
            print("   正在创建 flink_user 用户...")
            cursor.execute(f"CREATE USER {FLINK_USER} IDENTIFIED BY {FLINK_PASSWORD}")
            cursor.execute(f"GRANT CONNECT, RESOURCE, CREATE SESSION, CREATE TABLE TO {FLINK_USER}")
            cursor.execute(f"GRANT UNLIMITED TABLESPACE TO {FLINK_USER}")
            print(f"   ✓ 已创建 {FLINK_USER} 用户")
        else:
            print(f"   ✓ {FLINK_USER} 用户已存在")
        
        # 2. 删除 finance_user 中的旧表
        print("\n2. 检查并删除旧表...")
        cursor.execute("""
            SELECT COUNT(*) FROM dba_tables 
            WHERE owner = 'FINANCE_USER' AND table_name = 'LOG_MINING_FLUSH'
        """)
        count = cursor.fetchone()[0]
        
        if count > 0:
            print("   正在删除 finance_user.log_mining_flush...")
            cursor.execute("DROP TABLE finance_user.log_mining_flush")
            print("   ✓ 已删除 finance_user.log_mining_flush")
        else:
            print("   ✓ finance_user.log_mining_flush 不存在")
        
        # 删除 SYSTEM 中的表（如果存在）
        cursor.execute("""
            SELECT COUNT(*) FROM dba_tables 
            WHERE owner = 'SYSTEM' AND table_name = 'LOG_MINING_FLUSH'
        """)
        count = cursor.fetchone()[0]
        
        if count > 0:
            print("   正在删除 SYSTEM.log_mining_flush...")
            cursor.execute("DROP TABLE SYSTEM.log_mining_flush")
            print("   ✓ 已删除 SYSTEM.log_mining_flush")
        
        # 3. 在 flink_user 中创建表
        print("\n3. 在 flink_user 中创建表...")
        cursor.execute("""
            SELECT COUNT(*) FROM dba_tables 
            WHERE owner = 'FLINK_USER' AND table_name = 'LOG_MINING_FLUSH'
        """)
        count = cursor.fetchone()[0]
        
        if count == 0:
            print("   正在创建表...")
            # 切换到 flink_user
            flink_conn = cx_Oracle.connect(FLINK_USER, FLINK_PASSWORD, dsn)
            flink_cursor = flink_conn.cursor()
            flink_cursor.execute("""
                CREATE TABLE flink_user.log_mining_flush (
                    scn NUMBER(19,0) NOT NULL,
                    PRIMARY KEY (scn)
                )
            """)
            flink_conn.commit()
            flink_cursor.close()
            flink_conn.close()
            print("   ✓ 已创建 flink_user.log_mining_flush")
        else:
            print("   ✓ flink_user.log_mining_flush 已存在")
        
        # 4. 授予权限
        print("\n4. 授予权限...")
        try:
            cursor.execute("GRANT SELECT, INSERT, UPDATE, DELETE ON flink_user.log_mining_flush TO finance_user")
            print("   ✓ 已授予 finance_user 权限")
        except cx_Oracle.DatabaseError as e:
            error, = e.args
            if error.code == 1917:  # ORA-01917: user or role does not exist
                print("   ! finance_user 用户不存在")
            else:
                raise
        
        # 5. 验证
        print("\n5. 验证配置...")
        print("\n   用户信息:")
        cursor.execute("""
            SELECT username, account_status, created 
            FROM dba_users 
            WHERE username = 'FLINK_USER'
        """)
        for row in cursor:
            print(f"   - {row[0]}: {row[1]} (创建于: {row[2]})")
        
        print("\n   表位置:")
        cursor.execute("""
            SELECT owner, table_name, tablespace_name 
            FROM dba_tables 
            WHERE table_name = 'LOG_MINING_FLUSH'
        """)
        for row in cursor:
            print(f"   - {row[0]}.{row[1]} (表空间: {row[2]})")
        
        print("\n   权限:")
        cursor.execute("""
            SELECT grantee, privilege 
            FROM dba_tab_privs 
            WHERE table_name = 'LOG_MINING_FLUSH'
            ORDER BY grantee, privilege
        """)
        for row in cursor:
            print(f"   - {row[0]}: {row[1]}")
        
        conn.commit()
        cursor.close()
        conn.close()
        
        print("\n" + "=" * 50)
        print("✓ 修复完成")
        print("=" * 50)
        print("\n下一步:")
        print("1. 重新构建项目: mvn clean package -DskipTests")
        print("2. 重新构建 Docker 镜像: docker-compose build")
        print("3. 重启 Flink 集群: docker-compose restart")
        print("4. 提交新的 CDC 任务")
        print()
        
    except cx_Oracle.DatabaseError as e:
        error, = e.args
        print(f"\n✗ 数据库错误: {error.message}")
        sys.exit(1)
    except Exception as e:
        print(f"\n✗ 错误: {e}")
        sys.exit(1)

if __name__ == "__main__":
    try:
        import cx_Oracle
    except ImportError:
        print("错误: 需要安装 cx_Oracle")
        print("安装命令: pip install cx_Oracle")
        sys.exit(1)
    
    main()
