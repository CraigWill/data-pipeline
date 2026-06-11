import java.io.FileWriter;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class RebuildForCdc {
    static PrintWriter out;

    public static void main(String[] args) throws Exception {
        out = new PrintWriter(new FileWriter("rebuild_result.log"));
        log("=== RebuildForCdc started ===");
        Class.forName("com.oceanbase.jdbc.Driver");
        try (Connection c = DriverManager.getConnection(
                "jdbc:oceanbase://centos-ob:2881/CDC_ADMIN?compatibleMode=ORACLE",
                "cdc_admin@oratenant", "password")) {
            c.setAutoCommit(false);
            Statement s = c.createStatement();

            // Backup APP_CONFIG
            List<String[]> rows = new ArrayList<>();
            try {
                ResultSet rs = s.executeQuery("SELECT ID,CONFIG_KEY,CONFIG_VALUE,DESCRIPTION FROM APP_CONFIG");
                while (rs.next()) rows.add(new String[]{rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4)});
                log("APP_CONFIG: backed up " + rows.size() + " rows");
            } catch (Exception e) { log("APP_CONFIG: no data - " + e.getMessage()); }
            try { s.execute("DROP TABLE APP_CONFIG"); log("APP_CONFIG: dropped"); } catch (Exception e) { log("APP_CONFIG: drop skipped"); }
            s.execute("CREATE TABLE APP_CONFIG (ID NUMBER(10,0) PRIMARY KEY, CONFIG_KEY VARCHAR2(200), CONFIG_VALUE VARCHAR2(2000), DESCRIPTION VARCHAR2(500), CREATED_AT TIMESTAMP(6), UPDATED_AT TIMESTAMP(6))");
            log("APP_CONFIG: created (TIMESTAMP, no defaults)");
            int id = 1;
            for (String[] r : rows) {
                s.execute("INSERT INTO APP_CONFIG VALUES (" + id++ + "," + q(r[1]) + "," + q(r[2]) + "," + q(r[3]) + ",NULL,NULL)");
            }
            log("APP_CONFIG: restored " + rows.size() + " rows");

            // Rebuild CDC_DATASOURCES
            List<String[]> ds = new ArrayList<>();
            try {
                ResultSet rs = s.executeQuery("SELECT ID,NAME,TYPE,HOST,PORT,USERNAME,PASSWORD,SID,DESCRIPTION,STATUS FROM CDC_DATASOURCES");
                while (rs.next()) ds.add(new String[]{rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5), rs.getString(6), rs.getString(7), rs.getString(8), rs.getString(9), rs.getString(10)});
                log("CDC_DATASOURCES: backed up " + ds.size() + " rows");
            } catch (Exception e) { log("CDC_DATASOURCES: no data - " + e.getMessage()); }
            try { s.execute("DROP TABLE CDC_DATASOURCES"); log("CDC_DATASOURCES: dropped"); } catch (Exception e) {}
            s.execute("CREATE TABLE CDC_DATASOURCES (ID VARCHAR2(100) PRIMARY KEY, NAME VARCHAR2(200), TYPE VARCHAR2(50), HOST VARCHAR2(200), PORT NUMBER(10,0), USERNAME VARCHAR2(200), PASSWORD VARCHAR2(500), SID VARCHAR2(200), DESCRIPTION VARCHAR2(500), STATUS VARCHAR2(50), CREATED_AT TIMESTAMP(6), UPDATED_AT TIMESTAMP(6))");
            log("CDC_DATASOURCES: created (TIMESTAMP, no defaults)");
            for (String[] r : ds) {
                s.execute("INSERT INTO CDC_DATASOURCES VALUES (" + q(r[0]) + "," + q(r[1]) + "," + q(r[2]) + "," + q(r[3]) + "," + (r[4] != null ? r[4] : "NULL") + "," + q(r[5]) + "," + q(r[6]) + "," + q(r[7]) + "," + q(r[8]) + "," + q(r[9]) + ",NULL,NULL)");
            }
            log("CDC_DATASOURCES: restored " + ds.size() + " rows");

            // Rebuild CDC_TASKS
            try { s.execute("DROP TABLE CDC_TASKS"); log("CDC_TASKS: dropped"); } catch (Exception e) {}
            s.execute("CREATE TABLE CDC_TASKS (ID VARCHAR2(100) PRIMARY KEY, NAME VARCHAR2(200), DATASOURCE_ID VARCHAR2(100), SCHEMA_NAME VARCHAR2(200), TABLE_LIST VARCHAR2(2000), OUTPUT_PATH VARCHAR2(500), PARALLELISM NUMBER(10,0), SPLIT_SIZE NUMBER(10,0), STATUS VARCHAR2(50), FLINK_JOB_ID VARCHAR2(100), ERROR_MESSAGE VARCHAR2(2000), CREATED_AT TIMESTAMP(6), UPDATED_AT TIMESTAMP(6))");
            log("CDC_TASKS: created (TIMESTAMP, no defaults)");

            // Rebuild CDC_FILES
            try { s.execute("DROP TABLE CDC_FILES"); log("CDC_FILES: dropped"); } catch (Exception e) {}
            s.execute("CREATE TABLE CDC_FILES (ID VARCHAR2(100) PRIMARY KEY, FILE_PATH VARCHAR2(1000), FILE_NAME VARCHAR2(500), TABLE_NAME VARCHAR2(200), FILE_SIZE NUMBER(19,0), LINE_COUNT NUMBER(19,0), LAST_MODIFIED TIMESTAMP(6), CREATED_AT TIMESTAMP(6))");
            log("CDC_FILES: created (TIMESTAMP, no defaults)");

            // Rebuild RUNTIME_JOBS
            try { s.execute("DROP TABLE RUNTIME_JOBS"); log("RUNTIME_JOBS: dropped"); } catch (Exception e) {}
            s.execute("CREATE TABLE RUNTIME_JOBS (ID VARCHAR2(100) PRIMARY KEY, TASK_ID VARCHAR2(100), FLINK_JOB_ID VARCHAR2(100), JOB_NAME VARCHAR2(200), STATUS VARCHAR2(50), SCHEMA_NAME VARCHAR2(200), PARALLELISM NUMBER(10,0), SUBMIT_TIME TIMESTAMP(6), START_TIME TIMESTAMP(6), END_TIME TIMESTAMP(6), ERROR_MESSAGE VARCHAR2(2000), LAST_SAVEPOINT_PATH VARCHAR2(500), LAST_SAVEPOINT_TIME TIMESTAMP(6), TABLES VARCHAR2(2000))");
            log("RUNTIME_JOBS: created (TIMESTAMP, no defaults)");

            // Rebuild CDC_TEST (for CDC testing)
            try { s.execute("DROP TABLE CDC_TEST"); log("CDC_TEST: dropped"); } catch (Exception e) {}
            s.execute("CREATE TABLE CDC_TEST (ID NUMBER(10,0) PRIMARY KEY, NAME VARCHAR2(100), VALUE VARCHAR2(500), TS TIMESTAMP(6))");
            s.execute("INSERT INTO CDC_TEST VALUES (1, 'hello', 'world', SYSTIMESTAMP)");
            log("CDC_TEST: created with TIMESTAMP(6)");

            c.commit();
            log("=== All tables rebuilt successfully. No DEFAULT clauses. ===");
        } catch (Exception e) {
            log("ERROR: " + e.getMessage());
            e.printStackTrace(out);
        }
        out.flush();
        out.close();
    }

    static String q(String v) { return v == null ? "NULL" : "'" + v.replace("'", "''") + "'"; }

    static void log(String msg) {
        String ts = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date());
        String line = "[" + ts + "] " + msg;
        out.println(line);
        System.out.println(line);
    }
}
