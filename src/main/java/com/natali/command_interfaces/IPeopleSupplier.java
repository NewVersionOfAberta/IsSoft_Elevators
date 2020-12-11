package com.natali.command_interfaces;

import com.natali.Direction;
import com.natali.Person;

import java.util.List;

public interface IPeopleSupplier {
    List<Person> get(int weight, int floor, Direction direction);
}
