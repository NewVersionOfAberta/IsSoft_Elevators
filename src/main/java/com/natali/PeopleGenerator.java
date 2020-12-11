package com.natali;

import lombok.extern.slf4j.Slf4j;

import java.util.Random;
import java.util.function.Consumer;

@Slf4j
public class PeopleGenerator implements Runnable {
    private final int TIME_INTERVAL;

    private final int MAX_FLOOR;

    private final int MIN_FLOOR;
    private static final int MAX_WEIGHT = 150;
    private static final int MIN_WEIGHT = 20;
    private volatile boolean isRunning;
    private final Consumer<Person> addPerson;


    public PeopleGenerator(int TIME_INTERVAL, int MAX_FLOOR, int MIN_FLOOR, Consumer<Person> addPerson) {
        this.TIME_INTERVAL = TIME_INTERVAL;
        this.MAX_FLOOR = MAX_FLOOR;
        this.MIN_FLOOR = MIN_FLOOR;
        this.addPerson = addPerson;
        isRunning = true;
    }

    public void run() {
        Random random = new Random();
        int floor, targetFloor;
        Direction direction;
        Person person;

        while (isRunning) {
            floor = random.nextInt(MAX_FLOOR - MIN_FLOOR + 1) + MIN_FLOOR;

            do {
                targetFloor = random.nextInt(MAX_FLOOR - MIN_FLOOR + 1) + MIN_FLOOR;
            } while (targetFloor == floor);

            direction = floor > targetFloor ? Direction.Down : Direction.Up;
            person = new Person(random.nextInt(MAX_WEIGHT - MIN_WEIGHT) + MIN_WEIGHT,
                    targetFloor, floor, direction);
            addPerson.accept(person);
            try {
                Thread.sleep(TIME_INTERVAL);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}
