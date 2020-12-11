package com.natali;

import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

@Slf4j
public class Environment {

    private static final int TIME_TO_WAIT_AFTER_ELEVATOR_LEAVE = 150;

    private final int MAX_FLOOR;
    private final int MIN_FLOOR;
    private final Controller controller;
    private final StatisticsWriter statisticsWriter;

    private final ThreadPoolExecutor waitingPool;

    private final Map<Integer, ConcurrentLinkedQueue<Person>> floorQueueUp;
    private final Map<Integer, ConcurrentLinkedQueue<Person>> floorQueueDown;


    private volatile boolean isRunning;

    public Environment(int timeInterval, int max_floor, int min_floor, int elevatorsNumber,
                       int timeToOpenTheDoor, int timeToCloseTheDoor, int timeToPassAFloor, int bearingCapacity) {
        checkArgument(max_floor > min_floor, "");
        checkArgument(timeInterval > 0);
        checkArgument(timeToOpenTheDoor >= 0);
        checkArgument(timeToCloseTheDoor >= 0);
        checkArgument(timeToPassAFloor >= 0);
        checkArgument(bearingCapacity > 0);
        checkArgument(elevatorsNumber > 0);

        MAX_FLOOR = max_floor;
        MIN_FLOOR = min_floor;
        isRunning = true;

        statisticsWriter = new
                StatisticsWriter(MIN_FLOOR, MAX_FLOOR, "statistics.txt", elevatorsNumber);
        waitingPool = (ThreadPoolExecutor) Executors.newCachedThreadPool();

        floorQueueUp = new HashMap<>();
        floorQueueDown = new HashMap<>();
        initializeFloorMaps();
        controller = createController(elevatorsNumber,
                timeToOpenTheDoor, timeToCloseTheDoor, timeToPassAFloor, bearingCapacity);
        PeopleGenerator peopleGenerator = new PeopleGenerator(timeInterval, MAX_FLOOR, MIN_FLOOR, this::addPerson);

        new Thread(peopleGenerator).start();
        new Thread(statisticsWriter).start();

    }

    private void addPerson(Person person) {
        checkNotNull(person);
        Direction direction = person.getDirection();
        int floor = person.getStartFloor();
        int targetFloor = person.getTargetFloor();
        ConcurrentLinkedQueue<Person> queue = Direction.Down == direction ? floorQueueDown.get(floor) : floorQueueUp.get(floor);
        queue.add(person);
        log.info("A person appeared on the {} floor (target floor: {})", floor, targetFloor);

        //if there are more than 1 people generators then add synchronization
        if (queue.size() == 1) {
            controller.addClientParallel(direction, floor);
        }

    }

    private void initializeFloorMaps() {
        for (int i = MIN_FLOOR; i <= MAX_FLOOR; i++) {
            floorQueueDown.put(i, new ConcurrentLinkedQueue<>());
            floorQueueUp.put(i, new ConcurrentLinkedQueue<>());
        }
    }


    public void DelayedCall(Direction direction, int floor) {
        try {
            Thread.sleep(TIME_TO_WAIT_AFTER_ELEVATOR_LEAVE);
        } catch (InterruptedException e) {
            log.error("Thread was interrupted", e);
        }
        controller.addClientParallel(direction, floor);
    }


    public List<Person> getPersonsList(int weight, int floor, Direction direction) {
        Queue<Person> queue = direction == Direction.Down ? floorQueueDown.get(floor) : floorQueueUp.get(floor);
        int totalWeight = 0;
        List<Person> resultPersonsList = new ArrayList<>();
        Person person;
        // if for some reason more than one elevator picking up passengers from this floor
        synchronized (queue) {
            while (!queue.isEmpty() && queue.peek().getWeight() + totalWeight <= weight) {
                person = queue.poll();
                checkNotNull(person);
                totalWeight += person.getWeight();
                resultPersonsList.add(person);
            }
            if (!queue.isEmpty()) {
                waitingPool.submit(() -> DelayedCall(direction, floor));
            }
        }
        return resultPersonsList;
    }


    private Controller createController(int elevatorsNumber, int timeToOpenTheDoor,
                                        int timeToCloseTheDoor, int timeToPassAFloor, int bearingCapacity) {
        List<Elevator> elevators = new ArrayList<>();
        for (int i = 1; i <= elevatorsNumber; i++) {
            elevators.add(new Elevator(
                    timeToOpenTheDoor, timeToCloseTheDoor, timeToPassAFloor, MAX_FLOOR, MIN_FLOOR,
                    i, this::getPersonsList, statisticsWriter::getDataProcessor, MIN_FLOOR, bearingCapacity));
        }
        return new Controller(elevators, isRunning);
    }
}
