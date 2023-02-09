public class Tricky
{
    static String bar(int n, int k, String op) {
        if (k==0) {
            return "";
        }
        return op+n+"X"+bar(n-1,k-1,op)+"";
    }
    static String foo(int n) {
        String b = "";
        if (n<2) {
            b = b + "A";
        }
        for (int i=0; i<n; i++){
            b = b + "A";
        }
        String s = bar(n-1,n/2-1,"m");
        String t = bar(n-n/2,n-(n/2-1),"p");
        return b+n+(s+t).replace('X','B');
    }
    public static void main(String args[]) {
        int n = new Random().nextInt();
        // Tricky t = new Tricky();
        String res = foo(n);
        System.out.println(res);
    }
}

q0,) -> e;
q0,* -> e;
q0,+ -> e;
q1,) -> e;
q1,* -> e;
q1,+ -> e;
q2,( -> e;
q2,) -> e;
q2,i -> e;
q3,( -> e;
q3,) -> e;
q3,* -> e;
q3,+ -> e;
q4,( -> e;
q4,* -> e;
q4,+ -> e;
q4,i -> e;
q5,( -> e;
q5,) -> e;
q5,i -> e;