package camel_case_v10;

public class RandomUtils {
    public static int nextInt(int maxExclusive) {
        return (int) Math.floor(Math.random() * maxExclusive);
    }
}
