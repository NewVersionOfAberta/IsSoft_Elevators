package com.natali;

import com.natali.command_interfaces.Notifier;
import com.natali.command_interfaces.PeopleSupplier;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

@Slf4j
public class Elevator implements Runnable {
    private static final String WRONG_FLOOR_NUMBER = "Floor number must be in range from %i to %i";
    private static final int WEIGHT_DELTA = 30;
    /* Elevator configuration*/
    //time in milliseconds
    private final int timeToOpenTheDoor;
    private final int timeOneFloorPath;
    private final int timeToCloseTheDoor;
    private final int maxFloor;

    private final int minFloor;
    @Getter
    private final int id;
    private final int maxWeight;
    /* Communication */
    private final PeopleSupplier peopleSupplier;
    private final Function<Integer, StatisticProcessor> statisticProcessorFunction;
    private Notifier notifier;
    private final Condition hasClient;
    private final ReentrantLock lock;

    /* Additional fields to process Up and Down directions */

    private final HashMap<Direction, Comparator<Integer>> comparators;
    private final HashMap<Direction, Supplier<Integer>> nextFloorSupplier;
    private final List<Person> passengers;
    private final ConcurrentSkipListSet<Integer> floorSet;

    /*States*/

    private final AtomicInteger currentFloor;

    @Getter
    private volatile Direction actualDirection;
    @Getter
    private volatile Direction askedDirection;
    @Setter
    @Getter
    private volatile boolean isRunning;
    @Getter
    private boolean isOverweight;

    public Elevator(int timeToOpenTheDoor, int timeToCloseTheDoor, int timeOneFloorPath, int maxFloor, int minFloor,
                    int id, PeopleSupplier peopleSupplier, Function<Integer, StatisticProcessor> statisticProcessorFunction,
                    int currentFloor, int maxWeight) {
        checkArgument(currentFloor >= minFloor, WRONG_FLOOR_NUMBER, minFloor, maxFloor);
        checkArgument(currentFloor <= maxFloor, WRONG_FLOOR_NUMBER, minFloor, maxFloor);
        this.timeToOpenTheDoor = timeToOpenTheDoor;
        this.timeToCloseTheDoor = timeToCloseTheDoor;
        this.timeOneFloorPath = timeOneFloorPath;
        this.maxFloor = maxFloor;
        this.minFloor = minFloor;
        this.id = id;
        this.peopleSupplier = peopleSupplier;
        this.statisticProcessorFunction = statisticProcessorFunction;
        this.maxWeight = maxWeight;
        this.currentFloor = new AtomicInteger(currentFloor);
        this.floorSet = new ConcurrentSkipListSet<>();

        passengers = new ArrayList<>();
        comparators = new HashMap<>();
        nextFloorSupplier = new HashMap<>();

        isOverweight = false;
        actualDirection = Direction.Idle;
        askedDirection = Direction.Idle;
        isRunning = true;

        comparators.put(Direction.Down, Integer::compareTo);
        comparators.put(Direction.Up, Comparator.reverseOrder());
        nextFloorSupplier.put(Direction.Up, floorSet::pollFirst);
        nextFloorSupplier.put(Direction.Down, floorSet::pollLast);

        lock = new ReentrantLock();
        hasClient = lock.newCondition();
    }

    public int getCurrentFloor() {
        return currentFloor.get();
    }


    private void setAskedDirection(int floor, Direction direction) {
        if (floor == minFloor) {
            askedDirection = Direction.Down;
        } else if (floor == maxFloor) {
            askedDirection = Direction.Up;
        } else {
            askedDirection = direction;
        }
    }

    public void addFloor(Integer floor, Direction direction) {
        checkArgument(floor <= maxFloor || floor >= minFloor, WRONG_FLOOR_NUMBER, minFloor, maxFloor);
        if (actualDirection == Direction.Idle) {
            actualDirection = floor > currentFloor.get() ? Direction.Up : Direction.Down;
            setAskedDirection(floor, direction);
            floorSet.add(floor);
            lock.lock();
            hasClient.signal();
            lock.unlock();
            log.info("Wake up elevator {}", id);
        } else {
            floorSet.add(floor);
        }
    }

    private void waitForClient() {
        lock.lock();
        while (floorSet.isEmpty() && isRunning) {
            try {
                hasClient.await();
            } catch (InterruptedException e) {
                log.error("Thread {} was interrupted {}", Thread.currentThread().getId(), e);
                isRunning = false;
            } finally {
                lock.unlock();
            }
        }
    }

    private int updateTargetFloorValue(int targetFloor) {
        if (!floorSet.isEmpty()) {
            int newTargetFloor = nextFloorSupplier.get(askedDirection).get();
            if (comparators.get(askedDirection).compare(newTargetFloor, targetFloor) > 0) {
                floorSet.add(targetFloor);
                targetFloor = newTargetFloor;
            } else if (newTargetFloor != targetFloor) {
                floorSet.add(newTargetFloor);
            }
        }
        return targetFloor;
    }

    private void move() {
        int targetFloor = nextFloorSupplier.get(askedDirection).get();
        while (true) {
            synchronized (this) {
                actualDirection = targetFloor > currentFloor.get() ? Direction.Up : Direction.Down;
                targetFloor = updateTargetFloorValue(targetFloor);
                if (targetFloor == currentFloor.get()) {
                    break;
                }
                currentFloor.addAndGet(actualDirection == Direction.Up ? 1 : -1);
            }
            waiting(timeOneFloorPath);
        }

        if (currentFloor.get() == minFloor || currentFloor.get() == maxFloor) {
            askedDirection = currentFloor.get() == minFloor ? Direction.Up : Direction.Down;
            notifier.notifyEmpty();
        }
        //

        log.info("The elevator #{} arrived on the {} floor", id, currentFloor.get());
    }

    private void dropOffClients() {
        int oldSize = passengers.size();
        List<Person> leavingPassengers = passengers.stream()
                .filter(p -> p.getTargetFloor() == currentFloor.get())
                .collect(Collectors.toList());
        passengers.removeAll(leavingPassengers);
        int weight = passengers.stream()
                .mapToInt(Person::getWeight)
                .sum();
        if (maxWeight - weight >= WEIGHT_DELTA) {
            isOverweight = false;
        }
        statisticProcessorFunction.apply(id)
                .onDropPassengers(currentFloor.get(), LocalDateTime.now(), leavingPassengers);
        log.info("Elevator {} dropped off {} passengers on the {} floor, weight: {}",
                id, oldSize - passengers.size(), currentFloor.get(), weight);
    }

    private void pickUpClients() {
        actualDirection = askedDirection;
        int weight = passengers.stream()
                .mapToInt(Person::getWeight)
                .sum();

        List<Person> newClients = peopleSupplier.get(maxWeight - weight, currentFloor.get(), askedDirection);

        floorSet.addAll(newClients.stream()
                .map(Person::getTargetFloor)
                .collect(Collectors.toSet()));

        passengers.addAll(newClients);
        weight = passengers.stream()
                .mapToInt(Person::getWeight)
                .sum();

        if (maxWeight - weight < WEIGHT_DELTA) {
            isOverweight = true;
        }
        statisticProcessorFunction.apply(id)
                .onPickPassengers(currentFloor.get(), LocalDateTime.now(), newClients);
        log.info("Elevator {} picked up {} passengers on the {} floor, weight {}",
                id, newClients.size(), currentFloor.get(), weight);
    }

    //синхронизация: добавление этажа в сет и остановка лифта
    private synchronized void stop() {
        if (!floorSet.isEmpty()) {
            return;
        }
        actualDirection = Direction.Idle;
        askedDirection = Direction.Idle;
        notifier.notifyEmpty();
        log.info("The elevator {} is idle now", id);
    }

    private void waiting(int time) {
        try {
            Thread.sleep(time);
        } catch (InterruptedException e) {
            log.error("Thread {} was interrupted", Thread.currentThread().getId());
            isRunning = false;
        }
    }

    @Override
    public void run() {
        while (isRunning) {
            waitForClient();
            while (!floorSet.isEmpty() && isRunning) {
                move();
                waiting(timeToOpenTheDoor);
                dropOffClients();
                pickUpClients();
                waiting(timeToCloseTheDoor);
                stop();
            }
        }

    }

    public void setNotifier(Notifier notifier) {
        checkNotNull(notifier);
        this.notifier = notifier;
    }
}
