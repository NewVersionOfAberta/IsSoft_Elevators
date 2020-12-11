package com.natali;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import java.util.Properties;

import static java.util.Objects.requireNonNull;

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
