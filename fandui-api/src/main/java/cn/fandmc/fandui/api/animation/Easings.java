package cn.fandmc.fandui.api.animation;

/** Common immutable easing functions. */
public final class Easings {
    public static final Easing LINEAR = progress -> progress;
    public static final Easing EASE_IN = progress -> progress * progress * progress;
    public static final Easing EASE_OUT = progress -> {
        double inverse = 1.0 - progress;
        return 1.0 - inverse * inverse * inverse;
    };
    public static final Easing EASE_IN_OUT = progress -> progress < 0.5
            ? 4.0 * progress * progress * progress
            : 1.0 - Math.pow(-2.0 * progress + 2.0, 3.0) / 2.0;

    private Easings() {
    }
}
