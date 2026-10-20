import entities.Balance;
import entities.Money;
import entities.Payment;
import strategies.EqualSplit;

final SplitWise splitWise = new SplitWise();
long alice;
long bob;
long carol;
long dave;
long trip;

void main() throws Exception {
    testSetup();
    testGetGroupTotal();
    testUserBalance();
    testSimplifyDebts();
    testDeleteExpense();
    testConcurrentExpenses();
}

void testSetup() {
    alice = splitWise.createUser("Alice");
    bob = splitWise.createUser("Bob");
    carol = splitWise.createUser("Carol");
    dave = splitWise.createUser("Dave");

    trip = splitWise.createGroup("Goa Trip");
    splitWise.addMember(trip, alice);
    splitWise.addMember(trip, bob);
    splitWise.addMember(trip, carol);
    splitWise.addMember(trip, dave);

    splitWise.addExpense(trip, alice, Money.of(1000, 0), "Hotel", new EqualSplit());
    splitWise.addExpense(trip, bob, Money.of(100, 1), "Cab", new EqualSplit());
}

void testGetGroupTotal() {
    IO.println("Group total: " + splitWise.getGroupTotal(trip));
}

void testUserBalance() {
    printUserBalance("Carol", carol);
    splitWise.settleUp(trip, carol, alice, Money.of(250, 0));
    printUserBalance("Carol after settling with Alice", carol);
    printUserBalance("Alice", alice);
}

void printUserBalance(String label, long userId) {
    Balance balance = splitWise.getUserBalance(userId);
    IO.println(label + ": owes " + balance.owes() + ", owed " + balance.owed());
}

void testSimplifyDebts() {
    IO.println("Simplified payments:");
    for (Payment p : splitWise.simplifyDebts(trip)) {
        IO.println("  " + p.from().name() + " pays " + p.to().name() + " " + p.amount());
    }
}

void testConcurrentExpenses() throws Exception {
    long flat = splitWise.createGroup("Flat");
    List<Long> roommates = new ArrayList<>();
    for (String name : List.of("Eve", "Frank", "Grace", "Heidi")) {
        long id = splitWise.createUser(name);
        splitWise.addMember(flat, id);
        roommates.add(id);
    }

    try (ExecutorService pool = Executors.newFixedThreadPool(8)) {
        CountDownLatch start = new CountDownLatch(1);
        for (int t = 0; t < 8; t++) {
            pool.submit(() -> {
                start.await();
                for (int i = 0; i < 1000; i++) {
                    splitWise.addExpense(flat, roommates.get(i % 4), Money.of(4, 0), "Groceries", new EqualSplit());
                }
                return null;
            });
        }
        start.countDown();
    }

    Money expectedTotal = Money.of(8 * 1000 * 4, 0);
    boolean totalOk = splitWise.getGroupTotal(flat).equals(expectedTotal);
    boolean netsOk = roommates.stream().allMatch(id -> splitWise.getUserBalance(id).net().equals(Money.ZERO));
    IO.println("Concurrent expenses: total " + splitWise.getGroupTotal(flat) + " (expected "
            + expectedTotal + "), all nets zero: " + netsOk + " -> " + (totalOk && netsOk ? "PASS" : "FAIL"));
}

void testDeleteExpense() {
    Money totalBefore = splitWise.getGroupTotal(trip);
    Money carolBefore = splitWise.getUserBalance(carol).net();
    long dinner = splitWise.addExpense(trip, carol, Money.of(400, 0), "Dinner", new EqualSplit());
    printUserBalance("Carol after adding dinner", carol);
    splitWise.deleteExpense(trip, dinner);
    printUserBalance("Carol after deleting dinner", carol);
    boolean restored = splitWise.getGroupTotal(trip).equals(totalBefore) && splitWise.getUserBalance(carol).net().equals(carolBefore);
    IO.println("Delete restores total and balances: " + (restored ? "PASS" : "FAIL"));
}
