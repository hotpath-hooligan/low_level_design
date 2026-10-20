package strategies;

import entities.Money;
import entities.Split;
import entities.User;

import java.util.List;

public interface SplitStrategy {
    List<Split> split(Money amount, List<User> participants);
}
