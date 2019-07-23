
public class TestTools {

    /**
     * print an offset banner that has every 10 th char numbered
     */
    public static final void printOffsetBanner() {

        StringBuilder buf = new StringBuilder();
        buf.append("0");
        for (int x = 0; x < 120; ++x) {
            buf.append("=");
        }
        for (int x = 1; x < 12; ++x) {
            int X = 10 * x;
            String ins = String.format("%d", X);
            buf.replace(X - 1, X + ins.length(), ins);
        }
        System.out.println(buf);
    }
}
