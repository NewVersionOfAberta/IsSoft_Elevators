package com.natali;

import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static java.lang.Math.abs;


@Slf4j
public class Controller {
    private static final String NO_LIFT_FOUND = "List of elevators is empty";

    private final List<Elevator> elevators;
    private final Queue<StorageStructure> waitingQueue;
    private final ThreadPoolExecutor threadPoolExecutor;

    private final ReentrantLock lock;
    private final Condition elevatorFree;

    private volatile boolean isRunning;

    private volatile boolean hasFreeElevator;

    public Controller(List<Elevator> elevators, boolean launchNow) {
        this.elevators = elevators;
        lock = new ReentrantLock();
        elevatorFree = lock.newCondition();
        isRunning = false;
        waitingQueue = new LinkedBlockingQueue<>();
        threadPoolExecutor = (ThreadPoolExecutor) Executors.newCachedThreadPool();
        checkNotNull(elevators);
        checkArgument(!elevators.isEmpty(), NO_LIFT_FOUND);
        if (launchNow) {
            setRunning(true);
        }
    }

    private void receiveEmptySignal() {
        hasFreeElevator = true;
        lock.lock();
        elevatorFree.signal();
        lock.unlock();

    }

    public void setRunning(boolean running) {
        isRunning = running;
        if (!isRunning) {
            elevators.forEach(e -> e.setRunning(false));
        } else {
            log.info("Launching elevators");
            elevators.forEach((elevator) ->
            {
                elevator.setNotifier(this::receiveEmptySignal);
                elevator.setRunning(true);
                new Thread(elevator).start();
            });
            log.info("The elevators are running");
            new Thread(this::processWaitingQueue).start();
        }
    }


    private boolean isElevatorSuitable(Elevator elevator, Direction direction, int startFloor) {
        boolean isTargetLower;
        boolean isDirectedByClients;
        boolean isSameActualDirection;
        isTargetLower = startFloor < elevator.getCurrentFloor();
        isDirectedByClients = elevator.getAskedDirection() == elevator.getActualDirection();
        isSameActualDirection = direction == elevator.getActualDirection();
        return !elevator.isOverweight() && (elevator.getActualDirection() == Direction.Idle
                || ((isDirectedByClients || direction == elevator.getAskedDirection()) &&
                (!isDirectedByClients || isSameActualDirection) &&
                (!isDirectedByClients || ((elevator.getActualDirection() != Direction.Down || isTargetLower)
                        && (elevator.getActualDirection() != Direction.Up || !isTargetLower)))));
    }

    private Elevator getMostSuitableElevator(Direction direction, int startFloor) {

        Elevator bestFittedElevator = null;
        int curFloorDelta = Integer.MAX_VALUE;
        int newFloorDelta;

        for (Elevator elevator : elevators) {
            if (!isElevatorSuitable(elevator, direction, startFloor)) {
                continue;
            }
            newFloorDelta = abs(startFloor - elevator.getCurrentFloor());
            if (newFloorDelta < curFloorDelta) {
                bestFittedElevator = elevator;
                curFloorDelta = newFloorDelta;
            } else if (newFloorDelta == curFloorDelta && bestFittedElevator != null
                    && bestFittedElevator.getCurrentFloor() < elevator.getCurrentFloor()) {
                bestFittedElevator = elevator;
            }
        }
        return bestFittedElevator;
    }


    private void processWaitingQueue() {
        while (isRunning) {
            lock.lock();
            try {
                while (waitingQueue.isEmpty() && !hasFreeElevator) {
                    elevatorFree.await();
                }
            } catch (InterruptedException e) {
                log.error("Thread {} was interrupted", Thread.currentThread().getId());
            } finally {
                lock.unlock();
            }
            StorageStructure person;
            while (!waitingQueue.isEmpty()) {
                person = waitingQueue.poll();
                addClientParallel(person.getDirection(), person.getFloor());
            }
            hasFreeElevator = false;
        }
    }

    public void addClientParallel(Direction direction, int startFloor) {
        threadPoolExecutor.submit(() -> addClient(direction, startFloor));
    }

    public int addClient(Direction direction, int startFloor) {
        Elevator elevator = null;
        boolean isFound = false;
        while (!isFound) {
            elevator = getMostSuitableElevator(direction, startFloor);
            if (Objects.isNull(elevator)) {
                waitingQueue.add(new StorageStructure(startFloor, direction));
                log.info("Person (floor: {}, direction: {}) is waiting", startFloor, direction);
                return -1;
            }
            synchronized (elevator) {
                if (!isElevatorSuitable(elevator, direction, startFloor)) {
                    log.info("The elevator {} is no longer suitable for person (start: {}, direction: {}).",
                            elevator.getId(), startFloor, direction);
                } else {
                    log.info("The elevator {} was called to the {} floor", elevator.getId(), startFloor);
                    isFound = true;
                    elevator.addFloor(startFloor, direction);
                }
            }
        }
        return elevator.getId();
    }
}


