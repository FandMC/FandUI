package cn.fandmc.fandui.api.event;

/** Stable pointer button identity independent of host input classes. */
public final class PointerButton {
    public static final PointerButton PRIMARY = new PointerButton(0);
    public static final PointerButton SECONDARY = new PointerButton(1);
    public static final PointerButton MIDDLE = new PointerButton(2);

    private final int index;

    private PointerButton(int index) {
        this.index = index;
    }

    public static PointerButton of(int index) {
        if (index < 0) {
            throw new IllegalArgumentException("Pointer button index must not be negative");
        }
        return switch (index) {
            case 0 -> PRIMARY;
            case 1 -> SECONDARY;
            case 2 -> MIDDLE;
            default -> new PointerButton(index);
        };
    }

    public int index() {
        return index;
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof PointerButton button && index == button.index;
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(index);
    }

    @Override
    public String toString() {
        return "PointerButton[" + index + ']';
    }
}
