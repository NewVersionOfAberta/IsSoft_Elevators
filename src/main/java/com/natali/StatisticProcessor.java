package com.natali;

import lombok.Getter;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.lang.StrictMath.abs;

public class StatisticProcessor {
    private final int MIN_FLOOR;

    private final int[] startFloorAmount;
    private final int[] targetFloorAmount;

    @Getter
    private int totalWeight;
    @Getter
    private long totalTime;
    @Getter
    private int amountOfPassengers;
    @Getter
    private int totalFloorsPassed;

    //temp
    Map<Person, StatisticsPerson> personStatisticsTemp;

    public StatisticProcessor(int minFloor, int maxFloor) {
        MIN_FLOOR = minFloor;

        personStatisticsTemp = new HashMap<>();
        startFloorAmount = new int[maxFloor - minFloor + 1];
        targetFloorAmount = new int[maxFloor - minFloor + 1];
    }

    public int[] getStartFloorAmount() {
        return Arrays.copyOf(startFloorAmount, startFloorAmount.length);
    }

    public int[] getTargetFloorAmount() {
        return Arrays.copyOf(targetFloorAmount, targetFloorAmount.length);
    }

    private void  initializePersonStatistics(Person person, int floor, LocalDateTime time) {
        StatisticsPerson statisticsPerson = new StatisticsPerson(time, floor);
        personStatisticsTemp.put(person, statisticsPerson);
        startFloorAmount[floor - MIN_FLOOR] += 1;
    }

    private synchronized void addStatisticsInfo(Person person, int floor, LocalDateTime time) {
        StatisticsPerson statisticsPerson = personStatisticsTemp.get(person);
        long timePeriod = ChronoUnit.MILLIS.between(statisticsPerson.getStartTime(), time);
        totalTime += timePeriod;
        totalFloorsPassed += abs(floor - statisticsPerson.getStartFloor());
        targetFloorAmount[floor - MIN_FLOOR] += 1;
        totalWeight += person.getWeight();
        amountOfPassengers++;
    }

    public void onPickPassengers(int floor, LocalDateTime time, List<Person> people) {
        people.forEach(person -> initializePersonStatistics(person, floor, time));
    }

    public void onDropPassengers(int floor, LocalDateTime time, List<Person> people) {
        people.forEach(person -> addStatisticsInfo(person, floor, time));
    }
}
