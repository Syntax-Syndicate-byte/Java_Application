import java.sql.*;

class BankDatabase {
    private static final String url="jdbc:mysql://localhost:3306/bankdb";
    private static final String user="bankuser";
    private static final String password="stanoe@5";

    private static Connection getConnection() throws SQLException{
        return DriverManager.getConnection(url,user,password);
    }

    public static long loadBalance(String accountName) throws SQLException{
        String sql="SELECT balance FROM accounts WHERE accountName=?";
        try (Connection con=getConnection();
                PreparedStatement ps=con.prepareStatement(sql)) {

                    ps.setString(1, accountName);   //fills in the ?

                    try(ResultSet rs=ps.executeQuery()){
                        if (rs.next()){
                            return rs.getLong("balance");
                        }
                        throw new SQLException("No such account: " + accountName);
                    }
                }
    }
    public static void resetDemoData() throws SQLException{
        String updatesql="UPDATE accounts SET balance=? WHERE accountName=?";
        try(Connection con=getConnection();
                Statement st=con.prepareStatement(updatesql);
                PreparedStatement ps=con.prepareStatement(updatesql)){
                    st.executeUpdate("DELETE FROM transactions");   //Clears Transaction Log

                    /*B00 is reset to its Starting Balance*/
                    ps.setString(2, "B00");   //fills in the ? for accountName
                    ps.setLong(1, 100000);    //fills in the ? for balance
                    ps.executeUpdate();

                    /*C00 is reset to its Starting Balance*/
                    ps.setString(2, "C00");   //fills in the ? for accountName
                    ps.setLong(1, 250000);    //fills in the ? for balance
                    ps.executeUpdate();
                }
    }
    public static void recordTransaction(String accountName, String performedBy, String type,
                                     long amount, long balanceBefore, long balanceAfter)
        throws SQLException {

    String updateSql = "UPDATE accounts SET balance = ? WHERE accountName = ?";
    String insertSql = "INSERT INTO transactions "
            + "(accountName, performed_by, type, amount, balance_before, balance_after) "
            + "VALUES (?, ?, ?, ?, ?, ?)";

    try (Connection con = getConnection()) {
        con.setAutoCommit(false);            // start a transaction: nothing is saved yet

        try (PreparedStatement up = con.prepareStatement(updateSql);
             PreparedStatement ins = con.prepareStatement(insertSql)) {

            up.setLong(1, balanceAfter);
            up.setString(2, accountName);
            up.executeUpdate();

            ins.setString(1, accountName);
            ins.setString(2, performedBy);
            ins.setString(3, type);
            ins.setLong(4, amount);
            ins.setLong(5, balanceBefore);
            ins.setLong(6, balanceAfter);
            ins.executeUpdate();

            con.commit();                    // both statements are saved together
        } catch (SQLException e) {
            con.rollback();                  // neither is saved
            throw e;
        }
    }
    }
}