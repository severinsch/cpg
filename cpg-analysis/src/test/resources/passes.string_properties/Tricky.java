public class Tricky
{
    String bar(int n, int k, String op) {
        if (k==0) {
            return "";
        }
        return op+n+"]"+this.bar(n-1,k-1,op)+" ";
    }
    String foo(int n) {
        String b = "";
        if (n<2) {
            b = b + "(";
        }
        for (int i=0; i<n; i++){
            b = b + "(";
        }
        String s = this.bar(n-1,n/2-1,"*").trim();
        String t = this.bar(n-n/2,n-(n/2-1),"+").trim();
        return b+n+(s+t).replace(']',')');
    }
    public static void main(String args[]) {
        int n = new Random().nextInt();
        System.out.println(new Tricky().foo(n));
    }
}