public class Tricky
{
     String bar(int n, int k, String op) {
        if (k==0) {
            return "";
        }
        return op+n+"]"+bar(n-1,k-1,op)+"";
    }
     String foo(int n) {
        String b = "";
        if (n<2) {
            b = b + "(";
        }
        for (int i=0; i<n; i++){
            b = b + "(";
        }
        String s = bar(n-1,n/2-1,"*");
        String t = bar(n-n/2,n-(n/2-1),"+");
        return b+n+(s+t).replace(']',')');
    }
    public static void main(String args[]) {
        int n = new Random().nextInt();
        String res = new Tricky().foo(n);
        System.out.println(res);
    }
}