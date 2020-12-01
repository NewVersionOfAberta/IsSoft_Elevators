

public class Person {
    private final int weight;
    private final int targetFloor;
    private final int startFloor;
    private final Direction direction;

    public Person(int weight, int targetFloor, int startFloor, Direction direction) {
        this.weight = weight;
        this.targetFloor = targetFloor;
        this.startFloor = startFloor;
        this.direction = direction;
    }

    public int getWeight() {
        return weight;
    }

    public int getTargetFloor() {
        return targetFloor;
    }

    public int getStartFloor() {
        return startFloor;
    }

    public Direction getDirection() {
        return direction;
    }
}
