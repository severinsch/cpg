import java.sql.*;
public class Tricky
{
  public static void main(String[] args){
        int x = 0;
        x++;
        String start = "abc";
        String res = myFun(start);
        res = res + x;
        System.out.println(res);

        Connection c = new Connection();
        Statement stmnt = c.createStatement();
        stmnt.executeQuery(res);
        return;
    }

    public static String myFun(String input){
        String s = "";
        for(int x = 0; x < 3; x++){
            s = s + input;
        }
        if(!s.isEmpty()){
            s = s.replace('a', 'n');
        }
        return s;
    }
}