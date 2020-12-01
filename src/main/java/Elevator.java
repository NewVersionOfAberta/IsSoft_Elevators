import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.*;

@Slf4j
public class Elevator implements Runnable {
    private static final String WRONG_FLOOR_NUMBER = "Floor number must be in range from %i to %i";

    /* Elevator configuration*/
    //time in milliseconds
    private final int timeToOpenTheDoor;
    private final int timeOneFloorPath;

    private final int maxFloor;

    private final int minFloor;
    private final int id;
    private final int maxWeight;
    //ToDo: think about a not concurrent set, why atomic int?
    /**/
    private final Lock lock;
    private final Condition notEmptyQueue;
    private final Environment environment;
    private Controller controller;
    /* Additional fields to process Up and Down directions */

    private final HashMap<Direction, Comparator<Integer>> comparators;
    private final HashMap<Direction, Supplier<Integer>> nextFloorSupplier;
    private List<Person> passengers;
    /*States*/
    private final ConcurrentSkipListSet<Integer> floorSet;
    private final AtomicInteger currentFloor;
    private volatile Direction actualDirection;
    private volatile Direction askedDirection;
    private volatile boolean isRunning;

    public Elevator(int timeToOpenTheDoor, int timeOneFloorPath, int maxFloor, int minFloor,
                    int id, Environment environment, int currentFloor, int maxWeight) {
        this.timeToOpenTheDoor = timeToOpenTheDoor;
        this.timeOneFloorPath = timeOneFloorPath;
        this.maxFloor = maxFloor;
        this.minFloor = minFloor;
        this.id = id;
        this.environment = environment;
        this.maxWeight = maxWeight;

        this.currentFloor = new AtomicInteger(currentFloor);
        this.floorSet = new ConcurrentSkipListSet<>();
        lock = new ReentrantLock();
        passengers = new ArrayList<>();
        comparators = new HashMap<>();
        nextFloorSupplier = new HashMap<>();

        notEmptyQueue = lock.newCondition();
        actualDirection = Direction.Idle;
        askedDirection = Direction.Idle;
        isRunning = true;
        comparators.put(Direction.Down, Integer::compareTo);
        comparators.put(Direction.Up, Comparator.reverseOrder());
        nextFloorSupplier.put(Direction.Up, floorSet::pollFirst);
        nextFloorSupplier.put(Direction.Down, floorSet::pollLast);
    }

    public int getMaxFloor() {
        return maxFloor;
    }

    public int getMinFloor() {
        return minFloor;
    }

    private void setAskedDirection(int floor, Direction direction){
        if (floor == minFloor){
            askedDirection = Direction.Down;
        }else if (floor == maxFloor){
            askedDirection = Direction.Up;
        }else{
            askedDirection = direction;
        }
    }

    public void addFloor(Integer floor, Direction direction){
        checkArgument(floor <= maxFloor || floor >= minFloor, WRONG_FLOOR_NUMBER, minFloor, maxFloor);
        if (actualDirection == Direction.Idle){
            actualDirection = floor > currentFloor.get() ? Direction.Up : Direction.Down;
            setAskedDirection(floor, direction);
            floorSet.add(floor);
            lock.lock();
            notEmptyQueue.signal();
            lock.unlock();
            log.info("Wake up elevator {}", id);
        }else{
            floorSet.add(floor);
        }
        
    }

    public int getId() {
        return id;
    }

    public int getCurrentFloor() {
        return currentFloor.get();
    }

    public Direction getActualDirection() {
        return actualDirection;
    }

    private void waitForClient(){
        lock.lock();
        while (floorSet.isEmpty()) {
            try {
                notEmptyQueue.await();
            } catch (InterruptedException e) {
                log.error("Thread {} cannot wait", Thread.currentThread().getId());
                isRunning = false;
                break;
            } finally {
                lock.unlock();
            }
        }
    }

    private int processCollisions(int newNext, int oldNext){
        if (actualDirection == Direction.Up && newNext >= currentFloor.get()
        || actualDirection == Direction.Down && newNext <= currentFloor.get()){
            floorSet.add(oldNext);
            return newNext;
        }
        controller.addClientParallel(actualDirection, newNext);
        return oldNext;
    }

    private int getNextFloor(int nextFloor) {
        if (!floorSet.isEmpty()) {
            int newNext = nextFloorSupplier.get(askedDirection).get();
            if (comparators.get(askedDirection).compare(newNext, nextFloor) > 0) {
                nextFloor = processCollisions(newNext, nextFloor);
            } else {
                floorSet.add(newNext);
            }
        }
        return nextFloor;
    }


    private void move(){
        int nextFloor = nextFloorSupplier.get(askedDirection).get();
        while (currentFloor.get() != nextFloor) {
            if (nextFloor == currentFloor.get()) {
                return;
            }
            actualDirection = nextFloor > currentFloor.get() ? Direction.Up : Direction.Down;
            waiting(timeOneFloorPath);
            synchronized (this) {
                nextFloor = getNextFloor(nextFloor);
                currentFloor.addAndGet(actualDirection == Direction.Up ? 1 : -1);
            }
            log.info("Elevator {} on the {} floor, moving on the {} floor", id, currentFloor.get(), nextFloor);

        }
        //ToDo: think again
        if (currentFloor.get() == minFloor){
            askedDirection = Direction.Up;
            controller.getLock().lock();
            controller.getElevatorFree().signal();
            controller.getLock().unlock();
            actualDirection = askedDirection;
        }else if (currentFloor.get() == maxFloor){
            askedDirection = Direction.Down;
            controller.getLock().lock();
            controller.getElevatorFree().signal();
            controller.getLock().unlock();
            actualDirection = askedDirection;
        }
        //

        log.info("The elevator arrived on the {} floor", currentFloor.get());
    }

    private void dropOffClients(){
        int oldSize = passengers.size();
        passengers = passengers.stream()
                .filter((Person p) -> p.getTargetFloor() != currentFloor.get())
                .collect(Collectors.toList());
        log.info("Elevator {} dropped off {} passengers on the {} floor",
                id, oldSize - passengers.size(), currentFloor.get());
    }

    private void pickUpClients(){
        actualDirection = askedDirection;
        int weight = passengers.stream()
                .mapToInt(Person::getWeight)
                .sum();
        List<Person> newClients = environment
                .getPersonsList(maxWeight - weight, currentFloor.get(), askedDirection);
        floorSet.addAll(newClients.stream()
                .map(Person::getTargetFloor)
                .collect(Collectors.toSet()));
        passengers.addAll(newClients);
        log.info("Elevator {} picked up {} passengers on the {} floor, actual: {}, requested: {}",
                id, newClients.size(), currentFloor.get(), actualDirection, askedDirection);
    }

    //синхронизация: добавление этажа в сет и остановка лифта
    private synchronized void stop(){
        if (!floorSet.isEmpty()){
            return;
        }
        actualDirection = Direction.Idle;
        askedDirection = Direction.Idle;
        controller.setHasFreeElevator(true);
        controller.getLock().lock();
        controller.getElevatorFree().signal();
        controller.getLock().unlock();
        log.info("The elevator {} is idle now", id);
    }

    private void waiting(int time){
        try {
            Thread.sleep(time);
        } catch (InterruptedException e) {
            log.error("Thread {} was interrupted", Thread.currentThread().getId());
            isRunning = false;
        }
    }

    @Override
    public void run() {
        checkNotNull(floorSet);
        while(isRunning) {
            waitForClient();
            while (!floorSet.isEmpty()) {
                move();
                waiting(timeToOpenTheDoor);
                dropOffClients();
                pickUpClients();
                waiting(timeToOpenTheDoor);
                stop();
            }
        }

    }

    public boolean isRunning() {
        return isRunning;
    }

    public void setRunning(boolean running) {
        isRunning = running;
    }

    public void setController(Controller controller) {
        this.controller = controller;
    }

    public Direction getAskedDirection() {
        return askedDirection;
    }
}
