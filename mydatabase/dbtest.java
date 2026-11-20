import java.sql.SQLException;

public class dbtest {
    public static void main(String[] args) {
        try {
            
            System.out.println("B00 balance: " + BankDatabase.loadBalance("B00"));
            System.out.println("C00 balance: " + BankDatabase.loadBalance("C00"));
        } catch (SQLException e) {
            System.out.println("Database error: " + e.getMessage());
        }
    }
}