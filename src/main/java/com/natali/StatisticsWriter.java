package com.natali;

import lombok.extern.slf4j.Slf4j;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

@Slf4j
public class StatisticsWriter implements Runnable {
    private static final int TIME_TO_NEXT_STATISTICS_WRIGHT = 5000;
    private static final int MILLISECONDS_IN_HOUR = 1000 * 60 * 60;
    private static final int MILLISECONDS_IN_MINUTE = 1000 * 60;
    private static final int MILLISECONDS_IN_SECOND = 1000;
    private static final String BOARD = "--------------------------------| %d s |--------------------------------\n";
    private static final String STATISTICS_PATTERN
            = "Лифт #%d проехал %d этажей и перевез %d пассажиров\n" +
            "Пассажиры провели в лифте всего %s, в среднем %d ms\n" +
            "Перевезено: %d кг, средний вес пассажира: %d\n" +
            "Лифт чаще всего забирал пассажиров с %d этажа, высаживал пассажиров на %d этаже\n\n";

    //elevator - data
    private final int elevatorsNumber;
    private final Map<Integer, StatisticProcessor> dataProcessorsMap;
    private final int MIN_FLOOR;
    private final String FILE_NAME;

    private volatile boolean isRunning;

    public StatisticsWriter(int minFloor, int maxFloor, String file_name, int elevatorsNumber) {
        MIN_FLOOR = minFloor;
        FILE_NAME = file_name;
        this.elevatorsNumber = elevatorsNumber;
        checkNotNull(file_name);
        checkArgument(!file_name.isEmpty(), "No file name for statistics file");
        isRunning = true;
        dataProcessorsMap = new ConcurrentHashMap<>();
        for (int i = 1; i <= elevatorsNumber; i++){
            dataProcessorsMap.put(i, new StatisticProcessor(MIN_FLOOR, maxFloor));
        }
    }

    public StatisticProcessor getDataProcessor(int id){
        return dataProcessorsMap.get(id);
    }

    private String getStringPeriod(long period){
        long hours = period / MILLISECONDS_IN_HOUR;
        period -= hours * MILLISECONDS_IN_HOUR;
        long minutes = period / MILLISECONDS_IN_MINUTE;
        period -= minutes * MILLISECONDS_IN_MINUTE;
        long seconds = period /  MILLISECONDS_IN_SECOND;
        period -= seconds / MILLISECONDS_IN_SECOND;
        return String.format("%d ч %d мин %d с %d мс", hours, minutes, seconds, period);
    }

    private int getMostPopularFloor(int[] floors){
        int max = -1;
        int floorNumber = 0;
        for (int i = 0; i < floors.length; i++){
            if (floors[i] > max){
                max = floors[i];
                floorNumber = i;
            }
        }
        return floorNumber;
    }

    private void writeStatistics(int timestamp){
        FileWriter fileWriter;
        log.info("Writing statistics");
        try {
            fileWriter = new FileWriter(FILE_NAME, true);
        } catch (IOException e) {
            isRunning = false;
            log.error("Problems with statistics: {}", e.getMessage());
            return;
        }
        StatisticProcessor dataProcessor;
        long totalTime;
        String totalTimeInElevator;
        int totalWeight, totalPassengers;
        int[] startFloors, targetFloors;
        PrintWriter printWriter = new PrintWriter(fileWriter);
        printWriter.printf(BOARD, timestamp);
        for (int i = 1; i <=  elevatorsNumber; i++){
            dataProcessor = dataProcessorsMap.get(i);
            synchronized (dataProcessor) {
                totalTime = dataProcessor.getTotalTime();
                totalTimeInElevator = getStringPeriod(totalTime);
                totalPassengers = dataProcessor.getAmountOfPassengers();
                if (totalPassengers == 0){
                    totalPassengers = 1;
                }
                totalWeight = dataProcessor.getTotalWeight();
                startFloors = dataProcessor.getStartFloorAmount();
                targetFloors = dataProcessor.getTargetFloorAmount();
            }

            printWriter.printf(STATISTICS_PATTERN,
                    i,  dataProcessor.getTotalFloorsPassed(), dataProcessor.getAmountOfPassengers(),
                    totalTimeInElevator, totalTime / totalPassengers,
                    totalWeight, totalWeight / totalPassengers,
                    getMostPopularFloor(startFloors) + MIN_FLOOR, getMostPopularFloor(targetFloors) + MIN_FLOOR);
        }
        printWriter.close();
        try {
            fileWriter.close();
        } catch (IOException e) {
            isRunning = false;
            log.error("Problems with statistics: {}", e.getMessage());
        }
    }

    @Override
    public void run() {
        FileWriter fileWriter;
        try {
            fileWriter = new FileWriter(FILE_NAME);
            fileWriter.close();
        } catch (IOException e) {
            isRunning = false;
            log.error("Problems with statistics: {}", e.getMessage());
            return;
        }
        int timestamp = 0;
        while (isRunning){
            try {
                Thread.sleep(TIME_TO_NEXT_STATISTICS_WRIGHT);
            } catch (InterruptedException e) {
                isRunning = false;
                log.error("Thread #{} was interrupted", Thread.currentThread().getId());
            }
            timestamp += TIME_TO_NEXT_STATISTICS_WRIGHT / MILLISECONDS_IN_SECOND;
            writeStatistics(timestamp);
        }
    }
}
