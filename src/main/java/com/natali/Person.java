package com.natali;

import lombok.Value;

@Value
public class Person {
    int weight;
    int targetFloor;
    int startFloor;
    Direction direction;
}
