import com.natali.Controller;
import com.natali.Direction;
import com.natali.Elevator;
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

    private Elevator makeAnyElevatorOnTargetFloor(int id, int targetFloor){
        return new Elevator(ANY_TIME_TO_OPEN_THE_DOOR,
                ANY_TIME_TO_OPEN_THE_DOOR,
                ANY_TIME_TO_PASS_A_FLOOR,
                MAX_FLOOR, MIN_FLOOR,
                id, (w, f, direction)-> new ArrayList<>(),
                (i)->null, targetFloor, BEARING_CAPACITY);
    }

    @Test
    void addClient_2elevators4and6floorsPersonAt5_elevatorAt6Move() {
        //init
        int expectedId = 2;
        int actualId;

        int startFloor = 5;
        int floor4 = 4;
        int floor6 = 6;
        Direction anyDirection = Direction.Up;

        Elevator elevator4floor = makeAnyElevatorOnTargetFloor(1, floor4);
        Elevator elevator6floor = makeAnyElevatorOnTargetFloor(expectedId, floor6);

        List<Elevator> elevators = new ArrayList<>(List.of(elevator4floor, elevator6floor));
        Controller controller = new Controller(elevators, false);

        //
        actualId = controller.addClient(anyDirection, startFloor);
        //
        assertEquals(expectedId, actualId);
    }

    @Test
    @DisplayName("Elevators 1E and 2E on the 4 and 6 floors. Persons P1 and P2 on the 1 and 9 floors;" +
            "1E takes P1 and 2E takes P2")
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
        Elevator elevator4floor = makeAnyElevatorOnTargetFloor(firstExpectedId, floor4);
        Elevator elevator6floor = makeAnyElevatorOnTargetFloor(secondExpectedId, floor6);

        List<Elevator> elevators = new ArrayList<>(List.of(elevator4floor, elevator6floor));
        Controller controller = new Controller(elevators, false);

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

        Elevator elevator3floor = makeAnyElevatorOnTargetFloor(1, floor3);
        Elevator elevator8floor = makeAnyElevatorOnTargetFloor(expectedId, floor8);

        List<Elevator> elevators = new ArrayList<>(List.of(elevator3floor, elevator8floor));
        Controller controller = new Controller(elevators, false);

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

        int firstId = 1;
        int secondId = 2;

        int startFloor = 4;
        Direction direction1 = Direction.Up;
        Direction direction2 = Direction.Down;
        int floor1 = 1;
        Elevator elevator3floor = makeAnyElevatorOnTargetFloor(firstId, floor1);
        Elevator elevator8floor = makeAnyElevatorOnTargetFloor(secondId, floor1);

        List<Elevator> elevators = new ArrayList<>(List.of(elevator3floor, elevator8floor));
        Controller controller = new Controller(elevators, false);

        actualId1 = controller.addClient(direction1, startFloor);
        actualId2 = controller.addClient(direction2, startFloor);
        //
        assertNotEquals(actualId1, actualId2);
    }
}