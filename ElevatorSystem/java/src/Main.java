import clock.IntervalClock;
import clock.ManualClock;
import entities.CarState;
import entities.Direction;
import entities.ElevatorCar;
import entities.ElevatorStatus;
import entities.LookStrategy;
import service.CostBasedStrategy;
import service.ElevatorSystem;
import service.ParkingStrategy;
import service.ZonedParkingStrategy;

final ParkingStrategy parking = new ZonedParkingStrategy(10);
final ManualClock clock = new ManualClock();
final List<ElevatorStatus> seen = new CopyOnWriteArrayList<>();

void main() throws Exception {
    testDispatch();
    testRideThenPark();
    testSkipsOppositeCall();
    testConcurrentRequests();
    testRealTimeClock();
}

// cars start parked: with 3 cars on 10 floors, car 0 -> 0, car 1 -> 4, car 2 -> 9
ElevatorSystem newSystem(int numCars) {
    seen.clear();
    List<ElevatorCar> cars = IntStream.range(0, numCars)
            .mapToObj(id -> new ElevatorCar(new LookStrategy(), parking.parkingFloor(id, numCars)))
            .toList();
    ElevatorSystem system = new ElevatorSystem(cars, new CostBasedStrategy(), parking);
    system.addListener(seen::add);
    return system;
}

List<Integer> doorFloors(int car) {
    return seen.stream().filter(s -> s.elevatorId() == car && s.state() == CarState.DOORS_OPEN)
            .map(ElevatorStatus::floor).distinct().toList();
}

void check(String name, boolean ok) {
    IO.println(name + ": " + (ok ? "PASS" : "FAIL"));
}

void testDispatch() {
    try (ElevatorSystem system = newSystem(3)) {
        system.start(clock);
        check("Dispatch picks nearest car", system.requestElevator(7, Direction.DOWN).join() == 2
                && system.requestElevator(1, Direction.UP).join() == 0
                && system.requestElevator(5, Direction.UP).join() == 1);
    }
}

void testRideThenPark() {
    try (ElevatorSystem system = newSystem(3)) {
        system.start(clock);
        int car = system.requestElevator(7, Direction.DOWN).join();
        clock.advance(2);                  // 9 -> 8 -> 7, doors open
        system.selectFloor(car, 0).join(); // rider boards, presses 0
        clock.advance(60);                 // ride down, then back home to 9
    }
    check("Ride 7 -> 0, then park at 9", doorFloors(2).equals(List.of(7, 0, 9)));
}

void testSkipsOppositeCall() {
    try (ElevatorSystem system = newSystem(1)) {
        system.start(clock);
        system.selectFloor(0, 9).join();
        clock.advance(3);                  // going up, at floor 3
        system.requestElevator(5, Direction.DOWN).join();
        clock.advance(30);                 // up to 9, down to 5, then home to 0
    }
    check("Going up skips DOWN call at 5, serves it on the way down", doorFloors(0).equals(List.of(9, 5, 0)));
}

void testConcurrentRequests() {
    record Call(int floor, CompletableFuture<Integer> car) {}
    List<Call> calls = new CopyOnWriteArrayList<>();
    try (ElevatorSystem system = newSystem(3)) {
        system.start(clock);
        try (ExecutorService pool = Executors.newFixedThreadPool(8)) {
            pool.submit(() -> clock.advance(200)); // time keeps moving while riders press buttons
            IntStream.range(0, 400).forEach(i -> pool.submit(() -> {
                int floor = i % 10;
                Direction dir = floor == 9 || (floor > 0 && i % 2 == 0) ? Direction.DOWN : Direction.UP;
                calls.add(new Call(floor, system.requestElevator(floor, dir)));
            }));
        }
        clock.advance(500);
    }
    check("400 concurrent calls all served", calls.stream().allMatch(c -> doorFloors(c.car().join()).contains(c.floor())));
}

void testRealTimeClock() throws InterruptedException {
    try (ElevatorSystem system = newSystem(3);
         IntervalClock realClock = new IntervalClock(Duration.ofMillis(50))) { // clock closes before the cars
        system.start(realClock);
        system.requestElevator(7, Direction.DOWN).join();
        Thread.sleep(500);
    }
    check("Real-time clock moves car 2 to 7", doorFloors(2).contains(7));
}
