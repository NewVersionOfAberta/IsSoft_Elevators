import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;


@Slf4j
public class Environment {
    private static final int MAX_WEIGHT = 150;
    private static final int MIN_WEIGHT = 20;
    private static final int BEARING_CAPACITY = 600;
    private static final int TIME_TO_OPEN_THE_DOOR = 100;
    private static final int TIME_TO_PASS_THE_FLOOR = 500;

    private final int TIME_INTERVAL;
    private final int MAX_FLOOR;
    private final int MIN_FLOOR;
    private final Controller controller;

    private ConcurrentMap<Integer, ConcurrentLinkedQueue<Person>> floorQueueUp;
    private ConcurrentMap<Integer, ConcurrentLinkedQueue<Person>> floorQueueDown;

    private volatile boolean isRunning;

    public Environment(int timeInterval, int max_floor, int min_floor, int elevatorsNumber) {
        TIME_INTERVAL = timeInterval;
        MAX_FLOOR = max_floor;
        MIN_FLOOR = min_floor;
        isRunning = true;
        initializeFloorMaps();
        controller = createController(elevatorsNumber);
        generatePerson();
    }

    private void initializeFloorMaps() {
        floorQueueUp = new ConcurrentHashMap<>();
        floorQueueDown = new ConcurrentHashMap<>();
        for (int i = MIN_FLOOR; i <= MAX_FLOOR; i++) {
            floorQueueDown.put(i, new ConcurrentLinkedQueue<>());
            floorQueueUp.put(i, new ConcurrentLinkedQueue<>());
        }
    }

    private Controller createController(int elevatorsNumber) {
        List<Elevator> elevators = new ArrayList<>();
        for (int i = 1; i <= elevatorsNumber; i++) {
            elevators.add(new Elevator(
                    TIME_TO_OPEN_THE_DOOR, TIME_TO_PASS_THE_FLOOR, MAX_FLOOR, MIN_FLOOR,
                    i, this, MIN_FLOOR, BEARING_CAPACITY));
        }
        return new Controller(elevators);
    }


    private void generatePerson() {
        Random random = new Random();
        int floor, targetFloor;
        Direction direction;
        Person person;
        ConcurrentLinkedQueue<Person> queue;

        while (isRunning) {
            floor = random.nextInt(MAX_FLOOR - MIN_FLOOR + 1) + MIN_FLOOR;

            do {
                targetFloor = random.nextInt(MAX_FLOOR - MIN_FLOOR + 1) + MIN_FLOOR;
            } while (targetFloor == floor);

            direction = floor > targetFloor ? Direction.Down : Direction.Up;
            person = new Person(random.nextInt(MAX_WEIGHT - MIN_WEIGHT) + MIN_WEIGHT,
                    targetFloor, floor, direction);
            queue = Direction.Down == direction ? floorQueueDown.get(floor) : floorQueueUp.get(floor);
            queue.add(person);
            log.info("A person appeared on the {} floor (target floor: {})", floor, targetFloor);
            if (queue.size() == 1) {
                controller.addClientParallel(direction, floor);
            }
            try {
                Thread.sleep(TIME_INTERVAL);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }


    public List<Person> getPersonsList(int weight, int floor, Direction direction) {
        Queue<Person> queue;
        queue = direction == Direction.Down ? floorQueueDown.get(floor) : floorQueueUp.get(floor);
        int totalWeight = 0;
        List<Person> resultPersonsList = new ArrayList<>();
        // if for some reason more than one elevator picking up passengers from this floor
        synchronized (queue) {
            while (!queue.isEmpty() && queue.peek().getWeight() + totalWeight <= weight) {
                resultPersonsList.add(queue.poll());
            }
        }
        return resultPersonsList;
    }

    public synchronized void setRunning(boolean running) {
        isRunning = running;
    }
}
