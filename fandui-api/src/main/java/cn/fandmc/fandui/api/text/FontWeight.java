package cn.fandmc.fandui.api.text;

/** CSS-compatible numeric font weight from 1 through 1000. */
public record FontWeight(int value) {
    public static final FontWeight NORMAL = new FontWeight(400);
    public static final FontWeight BOLD = new FontWeight(700);

    public FontWeight {
        if (value < 1 || value > 1000) {
            throw new IllegalArgumentException("Font weight must be between 1 and 1000");
        }
    }
}
