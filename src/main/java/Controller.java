import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Queue;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;


import static com.google.common.base.Preconditions.*;
import static java.lang.Math.abs;



@Slf4j
public class Controller {
    private static final String NO_LIFT_FOUND = "List of elevators is empty";

    private final List<Elevator> elevators;
    private final Queue<StorageStructure> waitingQueue;
    private final ThreadPoolExecutor threadPoolExecutor;
    private final Lock lock;
    private final Condition elevatorFree;

    private volatile boolean isRunning;
    private volatile boolean hasFreeElevator;

    public Controller(List<Elevator> elevators) {
        this.elevators = elevators;
        isRunning = true;
        lock = new ReentrantLock();
        elevatorFree = lock.newCondition();
        elevators.forEach((Elevator e) -> e.setController(this));
        waitingQueue = new LinkedBlockingQueue<>();
        threadPoolExecutor = (ThreadPoolExecutor) Executors.newCachedThreadPool();
        log.info("Launching elevators");
        elevators.forEach((elevator)-> new Thread(elevator).start());
        log.info("The elevators are running");
        Thread waitingQueueHandler = new Thread(this::processWaitingQueue);
        waitingQueueHandler.start();
    }

    private Elevator getMostSuitableElevator(Direction direction, int startFloor) {
        Elevator bestFittedElevator = null;
        int curFloorDelta = Integer.MAX_VALUE;
        int newFloorDelta;
        boolean isTargetLower;
        boolean isDirectedByClients;
        boolean isSameActualDirection;
        for (Elevator elevator : elevators) {
            isTargetLower = startFloor < elevator.getCurrentFloor();
            isDirectedByClients = elevator.getAskedDirection() == elevator.getActualDirection();
            isSameActualDirection = direction == elevator.getActualDirection();
            //если лифт не стоит и при этом:
            //если лифт едет к пользователю, а направление запроса не совпадает
            //если лифт едет по запросам и направление не совпадает
            //или если едет по запросам пользователей в нужном направлении, однако находится слишком высоко/низко
            if (elevator.getActualDirection() != Direction.Idle && (
                    (!isDirectedByClients && direction != elevator.getAskedDirection()) ||
                    (isDirectedByClients && !isSameActualDirection) ||
                    (isDirectedByClients && ((elevator.getActualDirection() == Direction.Down && !isTargetLower)
                                    || (elevator.getActualDirection() == Direction.Up && isTargetLower))))) {
                continue;
            }
            newFloorDelta = abs(startFloor - elevator.getCurrentFloor());
            if (newFloorDelta < curFloorDelta) {
                bestFittedElevator = elevator;
                curFloorDelta = newFloorDelta;
            }else if (newFloorDelta == curFloorDelta && bestFittedElevator != null
                    && bestFittedElevator.getCurrentFloor() < elevator.getCurrentFloor()){
                bestFittedElevator = elevator;
            }
        }
        return bestFittedElevator;
    }

    public Lock getLock() {
        return lock;
    }

    private void processWaitingQueue(){
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
            while (!waitingQueue.isEmpty()){
                person = waitingQueue.poll();
                //log.info("Poll person {}", person);
                addClientParallel(person.getDirection(), person.getFloor());
            }
            hasFreeElevator = false;
        }
    }

    public Condition getElevatorFree() {
        return elevatorFree;
    }

    public void addClientParallel(Direction direction, int startFloor){
        threadPoolExecutor.submit(()->addClient(direction, startFloor));
    }

    public int addClient(Direction direction, int startFloor) {
        checkNotNull(elevators);
        checkArgument(!elevators.isEmpty(), NO_LIFT_FOUND);
        Elevator elevator = null;
        boolean isFound = false;
        while (!isFound) {
            elevator = getMostSuitableElevator(direction, startFloor);
            if (elevator == null) {
                waitingQueue.add(new StorageStructure(startFloor, direction));
                log.info("Person (floor: {}, direction: {}) is waiting", startFloor, direction);
                return -1;
            }
            //если лифт не стоит и при этом:
            //если лифт едет к пользователю, а направление запроса не совпадает
            //или если едет по запросам пользователей в нужном направлении, однако находится слишком высоко/низко
            synchronized (elevator) {
                boolean isTargetLower = startFloor < elevator.getCurrentFloor();
                boolean isDirectedByClients = elevator.getAskedDirection() == elevator.getActualDirection();
                boolean isSameActualDirection = direction == elevator.getActualDirection();

                if (elevator.getActualDirection() != Direction.Idle && ((!isDirectedByClients && isSameActualDirection) ||
                        (isDirectedByClients && !isSameActualDirection) ||
                        (isDirectedByClients && ((elevator.getActualDirection() == Direction.Down && !isTargetLower)
                                || (elevator.getActualDirection() == Direction.Up && isTargetLower))))) {
                    log.info("The elevator {} is no longer suitable.", elevator.getId());
                } else {
                    log.info("The elevator {} was called to the {} floor", elevator.getId(), startFloor);
                    isFound = true;
                    elevator.addFloor(startFloor, direction);
                }
            }
        }
        return elevator.getId();
    }

    public void setHasFreeElevator(boolean hasFreeElevator) {
        this.hasFreeElevator = hasFreeElevator;
    }
}


