package strategies;

import entities.Money;
import entities.Split;
import entities.User;

import java.util.ArrayList;
import java.util.List;

public class EqualSplit implements SplitStrategy {
    @Override
    public List<Split> split(Money amount, List<User> participants) {
        long share = amount.paise() / participants.size();
        long remainder = amount.paise() % participants.size();
        List<Split> splits = new ArrayList<>();
        for (int i = 0; i < participants.size(); i++) {
            splits.add(new Split(participants.get(i), new Money(share + (i < remainder ? 1 : 0))));
        }
        return splits;
    }
}
