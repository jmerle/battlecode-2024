package camel_case_v07;

public class RandomUtils {
    public static int nextInt(int maxExclusive) {
        return (int) Math.floor(Math.random() * maxExclusive);
    }
}
