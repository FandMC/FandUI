package cn.fandmc.fandui.api.layout;

/** Two-dimensional alignment represented by normalized horizontal and vertical factors. */
public enum Alignment {
    TOP_LEFT(0.0f, 0.0f),
    TOP_CENTER(0.5f, 0.0f),
    TOP_RIGHT(1.0f, 0.0f),
    CENTER_LEFT(0.0f, 0.5f),
    CENTER(0.5f, 0.5f),
    CENTER_RIGHT(1.0f, 0.5f),
    BOTTOM_LEFT(0.0f, 1.0f),
    BOTTOM_CENTER(0.5f, 1.0f),
    BOTTOM_RIGHT(1.0f, 1.0f);

    private final float horizontalFactor;
    private final float verticalFactor;

    Alignment(float horizontalFactor, float verticalFactor) {
        this.horizontalFactor = horizontalFactor;
        this.verticalFactor = verticalFactor;
    }

    public float horizontalFactor() {
        return horizontalFactor;
    }

    public float verticalFactor() {
        return verticalFactor;
    }
}
