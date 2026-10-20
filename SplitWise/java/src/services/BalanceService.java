package services;

import entities.Balance;
import entities.Group;
import entities.Money;
import entities.Payment;
import entities.Split;
import entities.User;
import strategies.SimplifyStrategy;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class BalanceService {
    private record UserGroupBalanceSheet(ReentrantLock lock, Map<User, Money> owes) {}

    private record GroupBalanceSheet(ReentrantReadWriteLock lock, Map<User, UserGroupBalanceSheet> users) {}

    private final Map<Long, GroupBalanceSheet> balancesByGroup = new ConcurrentHashMap<>();
    private final SimplifyStrategy simplifyStrategy;

    public BalanceService(SimplifyStrategy simplifyStrategy) {
        this.simplifyStrategy = simplifyStrategy;
    }

    private GroupBalanceSheet balancesOf(Group group) {
        return balancesByGroup.computeIfAbsent(group.getId(),
                id -> new GroupBalanceSheet(new ReentrantReadWriteLock(), new ConcurrentHashMap<>()));
    }

    private List<ReentrantLock> lock(GroupBalanceSheet balances, Collection<User> users) {
        List<ReentrantLock> locks = users.stream()
                .distinct()
                .sorted(User.BY_ID)
                .map(user -> balances.users().computeIfAbsent(user, u -> new UserGroupBalanceSheet(new ReentrantLock(), new HashMap<>())).lock())
                .toList();
        balances.lock().readLock().lock();
        locks.forEach(ReentrantLock::lock);
        return locks;
    }

    private void unlock(GroupBalanceSheet balances, List<ReentrantLock> locks) {
        locks.reversed().forEach(ReentrantLock::unlock);
        balances.lock().readLock().unlock();
    }

    private void addDebt(GroupBalanceSheet balances, User debtor, User creditor, Money amount) {
        balances.users().get(debtor).owes().merge(creditor, amount, Money::plus);
        balances.users().get(creditor).owes().merge(debtor, amount.negate(), Money::plus);
    }

    public void update(Group group, User paidBy, List<Split> splits) {
        Set<User> involved = new HashSet<>();
        involved.add(paidBy);
        splits.forEach(split -> involved.add(split.user()));
        GroupBalanceSheet balances = balancesOf(group);
        List<ReentrantLock> locks = lock(balances, involved);
        try {
            for (Split split : splits) {
                if (split.user() != paidBy) {
                    addDebt(balances, split.user(), paidBy, split.amount());
                }
            }
        } finally {
            unlock(balances, locks);
        }
    }

    public void settleUp(Group group, User from, User to, Money amount) {
        GroupBalanceSheet balances = balancesOf(group);
        List<ReentrantLock> locks = lock(balances, List.of(from, to));
        try {
            addDebt(balances, to, from, amount);
        } finally {
            unlock(balances, locks);
        }
    }

    public Balance getBalance(User user, Collection<Group> groups) {
        Money owes = Money.ZERO;
        Money owed = Money.ZERO;
        for (Group group : groups) {
            GroupBalanceSheet balances = balancesOf(group);
            List<ReentrantLock> locks = lock(balances, List.of(user));
            try {
                Balance balance = computeBalance(balances, user);
                owes = owes.plus(balance.owes());
                owed = owed.plus(balance.owed());
            } finally {
                unlock(balances, locks);
            }
        }
        return new Balance(owes, owed);
    }

    public List<Payment> simplifyDebts(Group group) {
        Map<User, Money> nets = new HashMap<>();
        GroupBalanceSheet balances = balancesOf(group);
        balances.lock().writeLock().lock();
        try {
            for (User user : balances.users().keySet()) {
                nets.put(user, computeBalance(balances, user).net());
            }
        } finally {
            balances.lock().writeLock().unlock();
        }
        return simplifyStrategy.simplify(nets);
    }

    private Balance computeBalance(GroupBalanceSheet balances, User user) {
        Money owes = Money.ZERO;
        Money owed = Money.ZERO;
        UserGroupBalanceSheet userBalances = balances.users().get(user);
        for (Money amount : userBalances == null ? List.<Money>of() : userBalances.owes().values()) {
            if (amount.isPositive()) {
                owes = owes.plus(amount);
            } else {
                owed = owed.plus(amount.negate());
            }
        }
        return new Balance(owes, owed);
    }
}
