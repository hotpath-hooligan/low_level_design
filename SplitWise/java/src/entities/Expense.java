package entities;

import java.util.List;

public record Expense(long id, User paidBy, Money amount, String description, List<Split> splits) {
    public Expense {
        splits = List.copyOf(splits);
    }
}
