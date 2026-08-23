package cn.fandmc.fandui.internal.validation;

public final class Preconditions {
    private Preconditions() {
    }

    public static float finite(float value, String name) {
        if (!Float.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be finite");
        }
        return value;
    }

    public static double finite(double value, String name) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be finite");
        }
        return value;
    }

    public static float nonNegative(float value, String name) {
        finite(value, name);
        if (value < 0.0f) {
            throw new IllegalArgumentException(name + " must not be negative");
        }
        return value;
    }

    public static double nonNegative(double value, String name) {
        finite(value, name);
        if (value < 0.0) {
            throw new IllegalArgumentException(name + " must not be negative");
        }
        return value;
    }

    public static float unit(float value, String name) {
        finite(value, name);
        if (value < 0.0f || value > 1.0f) {
            throw new IllegalArgumentException(name + " must be between 0 and 1");
        }
        return value;
    }
}
