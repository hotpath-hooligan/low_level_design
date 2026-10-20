package strategies;

import entities.Money;
import entities.Payment;
import entities.User;

import java.util.List;
import java.util.Map;

public interface SimplifyStrategy {
    List<Payment> simplify(Map<User, Money> nets);
}
