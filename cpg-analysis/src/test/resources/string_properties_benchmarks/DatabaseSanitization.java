import java.sql.*;
public class DatabaseSanitization
{
    public static void main(String[] args) throws SQLException{
        if(args.length < 3)
            return;
        String input = args[1];

        String param = args[2] == "id" ? "id" : "name";
        String sanitized = sanitize(input);

        Statement stmnt = (new Connection()).createStatement();
        stmnt.executeQuery("DELETE * FROM users WHERE " + param + " = \'" + sanitized + "\'");
    }

    public static String sanitize(String input){
        return input.replace('\'', ' ').replace('-', ' ');
    }
}