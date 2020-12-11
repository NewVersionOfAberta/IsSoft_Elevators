package com.natali;

import java.util.Objects;

public class Runner {
    private static final String PROPERTY_FILE_NAME = "elevators_environment.properties";

    private static void createEnvironment(){
        EnvironmentCreator environmentCreator = new EnvironmentCreator();
        Environment environment = environmentCreator.createFromFile(PROPERTY_FILE_NAME);
        if (Objects.isNull(environment)){
            environmentCreator.createDefault();
        }

    }


    public static void main(String... args){
        createEnvironment();
    }
}
