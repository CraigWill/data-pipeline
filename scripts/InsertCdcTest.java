import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
public class InsertCdcTest {
    public static void main(String[] a) throws Exception {
        Class.forName("com.oceanbase.jdbc.Driver");
        try (Connection c = DriverManager.getConnection(
                "jdbc:oceanbase://centos-ob:2881/CDC_ADMIN?compatibleMode=ORACLE",
                "cdc_admin@oratenant", "password")) {
            Statement s = c.createStatement();
            long id = System.currentTimeMillis() % 2000000000;
            s.execute("INSERT INTO CDC_TEST VALUES (" + id + ", 'cdc_verify', 'inserted_at_" + System.currentTimeMillis() + "', SYSTIMESTAMP)");
            s.execute("COMMIT");
            System.out.println("Inserted CDC_TEST ID=" + id);
        }
    }
}
