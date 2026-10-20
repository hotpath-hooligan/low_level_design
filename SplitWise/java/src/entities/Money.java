package entities;

public record Money(long paise) implements Comparable<Money> {
    public static final Money ZERO = new Money(0);

    public static Money of(long rupees, long paise) {
        return new Money(rupees * 100 + paise);
    }

    public Money plus(Money other) {
        return new Money(paise + other.paise);
    }

    public Money minus(Money other) {
        return new Money(paise - other.paise);
    }

    public Money negate() {
        return new Money(-paise);
    }

    public boolean isPositive() {
        return paise > 0;
    }

    @Override
    public int compareTo(Money other) {
        return Long.compare(paise, other.paise);
    }

    @Override
    public String toString() {
        long abs = Math.abs(paise);
        return (paise < 0 ? "-" : "") + "₹" + abs / 100 + "." + String.format("%02d", abs % 100);
    }
}
