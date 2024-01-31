package camel_case_v19_international_qualifiers;

public class Logger extends Globals {
    private static StringBuilder sb = new StringBuilder();

    public static void flush() {
        rc.setIndicatorString(sb.toString());
        sb = new StringBuilder();
    }

    public static void log(String message) {
        if (sb.length() > 0) {
            sb.append(", ");
        }

        sb.append(message);
    }
}