package ebulter.quote.lambda.wsmodel;

public class WsZenQuote {
    private String q;
    private String a;
    private String c;
    private String h;

    public WsZenQuote(String q, String a, String c, String h) {
        this.q = q;
        this.a = a;
        this.c = c;
        this.h = h;
    }

    public String getQ() {
        return q;
    }

    public void setQ(String q) {
        this.q = q;
    }

    public String getA() {
        return a;
    }

    public void setA(String a) {
        this.a = a;
    }

    public String getC() {
        return c;
    }

    public void setC(String c) {
        this.c = c;
    }

    public String getH() {
        return h;
    }

    public void setH(String h) {
        this.h = h;
    }
}
