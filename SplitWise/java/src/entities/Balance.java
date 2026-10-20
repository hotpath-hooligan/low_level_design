package entities;

public record Balance(Money owes, Money owed) {
    public Money net() {
        return owed.minus(owes);
    }
}
