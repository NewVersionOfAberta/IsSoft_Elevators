package com.natali;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import java.util.Properties;

import static java.util.Objects.requireNonNull;

@Slf4j
public class EnvironmentCreator {
    private static final int SPAWN_INTERVAL = 1000;
    private static final int MAX_FLOOR = 10;
    private static final int MIN_FLOOR = 1;
    private static final int ELEVATORS_NUMBER = 2;
    private static final int TIME_TO_OPEN_THE_DOOR = 10;
    private static final int TIME_TO_CLOSE_THE_DOOR = 10;
    private static final int TIME_TO_PASS_THE_FLOOR = 110;
    private static final int BEARING_CAPACITY = 400;


    public Environment createFromFile(String propertiesFile) {
        InputStream inputStream = null;
        int spawnTime, maxFloor, minFloor, elevatorNumber, timeToOpen, timeToClose, timeToPass, bearingCapacity;
        try {
            Properties prop = new Properties();

            inputStream = Runner.class.getClassLoader().getResourceAsStream(propertiesFile);

            if (inputStream != null) {
                prop.load(inputStream);
            } else {
                return null;
            }
            minFloor = Integer.parseInt(prop.getProperty("groundFloor"));
            maxFloor = Integer.parseInt(prop.getProperty("upperFloor"));
            elevatorNumber = Integer.parseInt(prop.getProperty("elevatorsNumber"));
            spawnTime = Integer.parseInt(prop.getProperty("personSpawnInterval"));
            timeToPass = Integer.parseInt(prop.getProperty("timeToPassAFloor"));
            timeToClose = Integer.parseInt(prop.getProperty("timeToOpenTheDoor"));
            timeToOpen = Integer.parseInt(prop.getProperty("timeToCloseTheDoor"));
            bearingCapacity = Integer.parseInt(prop.getProperty("bearingCapacity"));
        } catch (Exception e) {
            log.warn("Impossible to read properties file", e);
            return null;
        } finally {
            try {
                if (!Objects.isNull(inputStream)) {
                    (inputStream).close();
                }
            } catch (IOException e) {
                log.warn("Impossible to close input stream", e);
                return null;
            }
        }
        return new Environment(spawnTime, maxFloor, minFloor,
                elevatorNumber, timeToOpen, timeToClose, timeToPass, bearingCapacity);
    }

    public Environment createDefault() {
        return new Environment(SPAWN_INTERVAL, MAX_FLOOR, MIN_FLOOR, ELEVATORS_NUMBER,
                TIME_TO_OPEN_THE_DOOR, TIME_TO_CLOSE_THE_DOOR, TIME_TO_PASS_THE_FLOOR, BEARING_CAPACITY);
    }
}
