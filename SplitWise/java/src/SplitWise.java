import entities.Balance;
import entities.Money;
import entities.Payment;
import entities.User;
import services.BalanceService;
import services.ExpenseService;
import services.GroupService;
import services.UserService;
import strategies.GreedySimplify;
import strategies.SplitStrategy;

import java.util.List;

public class SplitWise {
    private final UserService userService = new UserService();
    private final GroupService groupService = new GroupService();
    private final BalanceService balanceService = new BalanceService(new GreedySimplify());
    private final ExpenseService expenseService = new ExpenseService(balanceService);

    long createUser(String name) {
        return userService.createUser(name).id();
    }

    long createGroup(String name) {
        return groupService.createGroup(name).getId();
    }

    void addMember(long groupId, long userId) {
        groupService.addMember(groupService.get(groupId), userService.get(userId));
    }

    long addExpense(long groupId, long paidById, Money amount, String description, SplitStrategy strategy) {
        return expenseService.addExpense(groupService.get(groupId), userService.get(paidById), amount, description, strategy);
    }

    void deleteExpense(long groupId, long expenseId) {
        expenseService.deleteExpense(groupService.get(groupId), expenseId);
    }

    void settleUp(long groupId, long fromId, long toId, Money amount) {
        balanceService.settleUp(groupService.get(groupId), userService.get(fromId), userService.get(toId), amount);
    }

    Money getGroupTotal(long groupId) {
        return expenseService.getTotal(groupService.get(groupId));
    }

    Balance getUserBalance(long userId) {
        User user = userService.get(userId);
        return balanceService.getBalance(user, groupService.groupsOf(user));
    }

    List<Payment> simplifyDebts(long groupId) {
        return balanceService.simplifyDebts(groupService.get(groupId));
    }
}
