// IGNORE_K2
// JVM_TARGET: 17
public record Rectangle(double length, double width) {
    public Rectangle {
        if (length <= 0 || width <= 0) throw new RuntimeException();
    }
}