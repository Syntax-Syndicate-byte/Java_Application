import java.sql.*;

public class connectiontest {
    public static void main(String[] args){
        String url="jdbc:mysql://localhost:3306/bankdb";
        String user="bankuser";
        String password="stanoe@5";

        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            System.out.println("Driver jar NOT on the classpath: " + e.getMessage());
        }

        try (Connection con=DriverManager.getConnection(url, user, password);
            Statement st=con.createStatement();
            ResultSet rs=st.executeQuery("SELECT VERSION()")){
                System.out.println("Connected to Database: " + con.getCatalog());
                if(rs.next()){
                    System.out.println("MySQL version: " + rs.getString(1));
                }
            }catch(SQLException e){
                System.out.println("Database error: " + e.getMessage());
            }
    }
    
}