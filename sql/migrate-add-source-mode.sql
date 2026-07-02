-- ============================================================
-- 迁移：为 CDC_TASKS 增加"采集方式"相关列（log / polling 双模式）
-- 适用：OceanBase Oracle 模式 / Oracle。非破坏性 ADD COLUMN，可回滚。
-- 说明：sourceMode=log 为默认（行为不变）；polling 为 JDBC 轮询增量。
-- ============================================================

ALTER TABLE CDC_ADMIN.CDC_TASKS ADD (
    SOURCE_MODE            VARCHAR2(20)  DEFAULT 'log',
    POLL_WATERMARK_COLUMN  VARCHAR2(128),
    POLL_WATERMARK_TYPE    VARCHAR2(20)  DEFAULT 'numeric',
    POLL_INTERVAL_MS       NUMBER(19),
    POLL_START_VALUE       VARCHAR2(256),
    POLL_OP                VARCHAR2(8)   DEFAULT 'c',
    POLL_MAX_BATCH         NUMBER(10)
);

COMMENT ON COLUMN CDC_ADMIN.CDC_TASKS.SOURCE_MODE           IS '采集方式: log(默认,经oblogproxy) / polling(JDBC轮询增量)';
COMMENT ON COLUMN CDC_ADMIN.CDC_TASKS.POLL_WATERMARK_COLUMN IS '轮询水位列(建议唯一单调列,如自增主键)';
COMMENT ON COLUMN CDC_ADMIN.CDC_TASKS.POLL_WATERMARK_TYPE   IS '水位类型: numeric / timestamp';
COMMENT ON COLUMN CDC_ADMIN.CDC_TASKS.POLL_INTERVAL_MS      IS '轮询间隔(毫秒)';
COMMENT ON COLUMN CDC_ADMIN.CDC_TASKS.POLL_START_VALUE      IS '起始水位值(可空: 数值起点或起始epoch毫秒)';
COMMENT ON COLUMN CDC_ADMIN.CDC_TASKS.POLL_OP              IS 'CSV操作标签(c/u)';
COMMENT ON COLUMN CDC_ADMIN.CDC_TASKS.POLL_MAX_BATCH       IS '单批最大行数';

COMMIT;

-- 回滚：
-- ALTER TABLE CDC_ADMIN.CDC_TASKS DROP (SOURCE_MODE, POLL_WATERMARK_COLUMN, POLL_WATERMARK_TYPE,
--     POLL_INTERVAL_MS, POLL_START_VALUE, POLL_OP, POLL_MAX_BATCH);
