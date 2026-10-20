package services;

import entities.Expense;
import entities.Group;
import entities.Money;
import entities.Split;
import entities.User;
import strategies.SplitStrategy;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

public class ExpenseService {
    private final AtomicLong nextExpenseId = new AtomicLong();
    private final Map<Long, List<Expense>> expensesByGroup = new ConcurrentHashMap<>();
    private final BalanceService balanceService;

    public ExpenseService(BalanceService balanceService) {
        this.balanceService = balanceService;
    }

    public long addExpense(Group group, User paidBy, Money amount, String description, SplitStrategy strategy) {
        List<User> members = group.getMembers().stream().sorted(User.BY_ID).toList();
        List<Split> splits = strategy.split(amount, members);
        Expense expense = new Expense(nextExpenseId.incrementAndGet(), paidBy, amount, description, splits);
        expensesByGroup.computeIfAbsent(group.getId(), id -> new CopyOnWriteArrayList<>()).add(expense);
        balanceService.update(group, paidBy, expense.splits());
        return expense.id();
    }

    public void deleteExpense(Group group, long expenseId) {
        List<Expense> expenses = expensesByGroup.getOrDefault(group.getId(), List.of());
        Expense expense = expenses.stream().filter(e -> e.id() == expenseId).findFirst().orElseThrow();
        if (expenses.remove(expense)) {
            List<Split> reversed = expense.splits().stream().map(s -> new Split(s.user(), s.amount().negate())).toList();
            balanceService.update(group, expense.paidBy(), reversed);
        }
    }

    public Money getTotal(Group group) {
        return expensesByGroup.getOrDefault(group.getId(), List.of()).stream().map(Expense::amount).reduce(Money.ZERO, Money::plus);
    }
}
