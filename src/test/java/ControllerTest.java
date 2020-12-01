import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ControllerTest {
    private static final int MAX_FLOOR = 9;
    private static final int MIN_FLOOR = 1;
    private static final int ANY_TIME_TO_OPEN_THE_DOOR = 200;
    private static final int ANY_TIME_TO_PASS_A_FLOOR = 2000;
    private static final int BEARING_CAPACITY = 600;

    @Test
    void addClient_2elevators4and6floorsPersonAt5_elevatorAt6Move() {
        //init
        int expectedId = 2;
        int actualId;

        int startFloor = 5;
        int floor4 = 4;
        int floor6 = 6;
        Direction anyDirection = Direction.Up;
        //Environment environment = new Environment(0, MAX_FLOOR, MIN_FLOOR, 2);
        Elevator elevator4floor = new Elevator(ANY_TIME_TO_OPEN_THE_DOOR, ANY_TIME_TO_PASS_A_FLOOR,
                MAX_FLOOR, MIN_FLOOR, 1, null, floor4, BEARING_CAPACITY);
        Elevator elevator6floor = new Elevator(ANY_TIME_TO_OPEN_THE_DOOR, ANY_TIME_TO_PASS_A_FLOOR,
                MAX_FLOOR, MIN_FLOOR, expectedId, null, floor6, BEARING_CAPACITY);

        List<Elevator> elevators = new ArrayList<>(List.of(elevator4floor, elevator6floor));
        Controller controller = new Controller(elevators);

        //
        actualId = controller.addClient(anyDirection, startFloor);
        //
        assertEquals(expectedId, actualId);
    }

    @Test
    @DisplayName("Elevators A and B on the 4 and 6 floors. Persons P1 and P2 on the 1 and 9 floors;" +
            "A takes P1 and B takes P2")
    void addClient_2personsOn1and9floors2elevatorsOn4and6_elevatorOn4takes1stOn6take2nd() {
        int firstExpectedId = 1;
        int firstActualId;
        int secondExpectedId = 2;
        int secondActualId;

        int firstStartFloor = 1;
        int secondStartFloor = 9;
        Direction anyDirection = Direction.Up;

        int floor4 = 4;
        int floor6 = 6;
       // Environment environment = new Environment(0, MAX_FLOOR, MIN_FLOOR, 2);
        Elevator elevator4floor = new Elevator(ANY_TIME_TO_OPEN_THE_DOOR, ANY_TIME_TO_PASS_A_FLOOR,
                MAX_FLOOR, MIN_FLOOR, firstExpectedId, null, floor4, BEARING_CAPACITY);
        Elevator elevator6floor = new Elevator(ANY_TIME_TO_OPEN_THE_DOOR, ANY_TIME_TO_PASS_A_FLOOR,
                MAX_FLOOR, MIN_FLOOR, secondExpectedId, null, floor6, BEARING_CAPACITY);

        List<Elevator> elevators = new ArrayList<>(List.of(elevator4floor, elevator6floor));
        Controller controller = new Controller(elevators);

        //
        firstActualId = controller.addClient(anyDirection, firstStartFloor);
        secondActualId = controller.addClient(anyDirection, secondStartFloor);
        //
        assertEquals(firstExpectedId, firstActualId);
        assertEquals(secondExpectedId, secondActualId);
    }

    private void moveElevatorDown(Controller controller, int currentFloor){
        controller.addClient(Direction.Down, currentFloor);
    }

    @Test
    @DisplayName("Elevators A and B on the 3 and 8 floors. Elevator A is moving down, elevator B is idle. " +
            "Persons P on the 4 floor; B takes P")
    void addClient_1personOn4floor2elevatorsOn3and8_elevatorOn8takesPerson() {
        int expectedId = 2;
        int actualId;

        int startFloor = 4;
        Direction anyDirection = Direction.Up;
        int floor3 = 3;
        int floor8 = 8;

        //Environment environment = new Environment(0, MAX_FLOOR, MIN_FLOOR, 2);
        Elevator elevator3floor = new Elevator(ANY_TIME_TO_OPEN_THE_DOOR, ANY_TIME_TO_PASS_A_FLOOR,
                MAX_FLOOR, MIN_FLOOR, 1, null, floor3, BEARING_CAPACITY);
        Elevator elevator8floor = new Elevator(ANY_TIME_TO_OPEN_THE_DOOR, ANY_TIME_TO_PASS_A_FLOOR,
                MAX_FLOOR, MIN_FLOOR, expectedId, null, floor8, BEARING_CAPACITY);

        List<Elevator> elevators = new ArrayList<>(List.of(elevator3floor, elevator8floor));
        Controller controller = new Controller(elevators);

        moveElevatorDown(controller, floor3);
        assertEquals(Direction.Down, elevator3floor.getActualDirection());

        //
        actualId = controller.addClient(anyDirection, startFloor);
        //
        assertEquals(expectedId, actualId);
        assertEquals(Direction.Down, elevator8floor.getActualDirection());
    }

    @Test
    void addClient_2personOn4floor2elevatorsOn1_1elevatorTakes1Person2elevatorTakes2person() {
        int actualId1;
        int actualId2;

        int startFloor = 4;
        Direction direction1 = Direction.Up;
        Direction direction2 = Direction.Down;
        int floor1 = 1;
        //Environment environment = new Environment(0, MAX_FLOOR, MIN_FLOOR, 2);
        Elevator elevator3floor = new Elevator(ANY_TIME_TO_OPEN_THE_DOOR, ANY_TIME_TO_PASS_A_FLOOR,
                MAX_FLOOR, MIN_FLOOR, 1, null, floor1, BEARING_CAPACITY);
        Elevator elevator8floor = new Elevator(ANY_TIME_TO_OPEN_THE_DOOR, ANY_TIME_TO_PASS_A_FLOOR,
                MAX_FLOOR, MIN_FLOOR, 2, null, floor1, BEARING_CAPACITY);

        List<Elevator> elevators = new ArrayList<>(List.of(elevator3floor, elevator8floor));
        Controller controller = new Controller(elevators);
//        Person person1 = new Person(DEFAULT_WEIGHT, upFloor, startFloor, Direction.Up);
//        Person person2 = new Person(DEFAULT_WEIGHT, downFloor, startFloor, Direction.Down);

        //
        actualId1 = controller.addClient(direction1, startFloor);
        actualId2 = controller.addClient(direction2, startFloor);
        //
        assertNotEquals(actualId1, actualId2);
    }
}