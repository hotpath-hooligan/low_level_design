package strategies;

import entities.Money;
import entities.Payment;
import entities.User;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

public class GreedySimplify implements SimplifyStrategy {
    private static final Comparator<Map.Entry<User, Money>> LARGEST_FIRST =
            Map.Entry.comparingByValue(Comparator.reverseOrder());

    @Override
    public List<Payment> simplify(Map<User, Money> nets) {
        PriorityQueue<Map.Entry<User, Money>> creditors = new PriorityQueue<>(LARGEST_FIRST);
        PriorityQueue<Map.Entry<User, Money>> debtors = new PriorityQueue<>(LARGEST_FIRST);
        for (Map.Entry<User, Money> entry : nets.entrySet()) {
            if (entry.getValue().isPositive()) {
                creditors.add(entry);
            } else if (entry.getValue().negate().isPositive()) {
                debtors.add(Map.entry(entry.getKey(), entry.getValue().negate()));
            }
        }

        List<Payment> payments = new ArrayList<>();
        while (!creditors.isEmpty() && !debtors.isEmpty()) {
            Map.Entry<User, Money> creditor = creditors.poll();
            Map.Entry<User, Money> debtor = debtors.poll();
            Money amount = creditor.getValue().compareTo(debtor.getValue()) <= 0 ? creditor.getValue() : debtor.getValue();
            payments.add(new Payment(debtor.getKey(), creditor.getKey(), amount));
            Money creditLeft = creditor.getValue().minus(amount);
            if (creditLeft.isPositive()) {
                creditors.add(Map.entry(creditor.getKey(), creditLeft));
            }
            Money debtLeft = debtor.getValue().minus(amount);
            if (debtLeft.isPositive()) {
                debtors.add(Map.entry(debtor.getKey(), debtLeft));
            }
        }
        return payments;
    }
}
