package cn.fandmc.fandui.api.icon;

import cn.fandmc.fandui.api.canvas.ArcDirection;
import cn.fandmc.fandui.api.canvas.LineCap;
import cn.fandmc.fandui.api.canvas.LineJoin;
import cn.fandmc.fandui.api.canvas.Path;
import cn.fandmc.fandui.api.canvas.StrokeStyle;
import cn.fandmc.fandui.api.layout.Rect;
import cn.fandmc.fandui.api.style.CornerRadii;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Small dependency-free preset icon catalogue for common UI actions. */
public final class Icons {
    private static final float TAU = (float) (Math.PI * 2.0);
    private static final Map<String, IconDefinition> CATALOGUE = new LinkedHashMap<>();

    public static final IconDefinition CHECK = preset("CHECK", stroke(
            Path.builder().moveTo(4.0f, 12.0f).lineTo(9.5f, 17.0f).lineTo(20.0f, 6.0f).build()));
    public static final IconDefinition CHECKMARK = preset("CHECKMARK", CHECK);
    public static final IconDefinition CLOSE = preset("CLOSE", stroke(
            Path.builder().moveTo(5.0f, 5.0f).lineTo(19.0f, 19.0f)
                    .moveTo(19.0f, 5.0f).lineTo(5.0f, 19.0f).build()));
    public static final IconDefinition PLUS = preset("PLUS", stroke(
            Path.builder().moveTo(12.0f, 4.0f).lineTo(12.0f, 20.0f)
                    .moveTo(4.0f, 12.0f).lineTo(20.0f, 12.0f).build()));
    public static final IconDefinition MINUS = preset("MINUS", stroke(
            Path.builder().moveTo(4.0f, 12.0f).lineTo(20.0f, 12.0f).build()));
    public static final IconDefinition CHEVRON_DOWN = preset("CHEVRON_DOWN", stroke(
            Path.builder().moveTo(5.0f, 9.0f).lineTo(12.0f, 16.0f).lineTo(19.0f, 9.0f).build()));
    public static final IconDefinition CHEVRON_UP = preset("CHEVRON_UP", stroke(
            Path.builder().moveTo(5.0f, 15.0f).lineTo(12.0f, 8.0f).lineTo(19.0f, 15.0f).build()));
    public static final IconDefinition CHEVRON_LEFT = preset("CHEVRON_LEFT", stroke(
            Path.builder().moveTo(15.0f, 5.0f).lineTo(8.0f, 12.0f).lineTo(15.0f, 19.0f).build()));
    public static final IconDefinition CHEVRON_RIGHT = preset("CHEVRON_RIGHT", stroke(
            Path.builder().moveTo(9.0f, 5.0f).lineTo(16.0f, 12.0f).lineTo(9.0f, 19.0f).build()));
    public static final IconDefinition ARROW_DOWN = preset("ARROW_DOWN", CHEVRON_DOWN);
    public static final IconDefinition ARROW_UP = preset("ARROW_UP", CHEVRON_UP);
    public static final IconDefinition ARROW_LEFT = preset("ARROW_LEFT", CHEVRON_LEFT);
    public static final IconDefinition ARROW_RIGHT = preset("ARROW_RIGHT", CHEVRON_RIGHT);
    public static final IconDefinition MENU = preset("MENU", stroke(
            Path.builder().moveTo(4.0f, 6.0f).lineTo(20.0f, 6.0f)
                    .moveTo(4.0f, 12.0f).lineTo(20.0f, 12.0f)
                    .moveTo(4.0f, 18.0f).lineTo(20.0f, 18.0f).build()));
    public static final IconDefinition SEARCH = preset("SEARCH", stroke(
            circle(10.5f, 10.5f, 6.0f),
            Path.builder().moveTo(15.0f, 15.0f).lineTo(20.0f, 20.0f).build()));
    public static final IconDefinition INFO = preset("INFO", stroke(
            circle(12.0f, 12.0f, 8.0f),
            Path.builder().moveTo(12.0f, 10.5f).lineTo(12.0f, 17.0f)
                    .moveTo(12.0f, 7.0f).lineTo(12.0f, 7.1f).build()));
    public static final IconDefinition PLAY = preset("PLAY", fill(
            Path.builder().moveTo(7.0f, 4.0f).lineTo(19.0f, 12.0f)
                    .lineTo(7.0f, 20.0f).close().build()));
    public static final IconDefinition PAUSE = preset("PAUSE", fill(
            Path.builder().rect(new Rect(6.0f, 4.0f, 4.0f, 16.0f)).build(),
            Path.builder().rect(new Rect(14.0f, 4.0f, 4.0f, 16.0f)).build()));
    public static final IconDefinition STOP = preset("STOP", fill(
            roundedRect(5.0f, 5.0f, 14.0f, 14.0f, 1.5f)));
    public static final IconDefinition WARNING = preset("WARNING", stroke(
            Path.builder().moveTo(12.0f, 3.0f).lineTo(22.0f, 20.0f)
                    .lineTo(2.0f, 20.0f).close().build(),
            Path.builder().moveTo(12.0f, 9.0f).lineTo(12.0f, 14.0f)
                    .moveTo(12.0f, 17.0f).lineTo(12.0f, 17.1f).build()));

    public static final IconDefinition HOME = preset("HOME", stroke(
            Path.builder().moveTo(3.0f, 11.0f).lineTo(12.0f, 3.5f).lineTo(21.0f, 11.0f)
                    .moveTo(5.0f, 9.5f).lineTo(5.0f, 20.0f).lineTo(19.0f, 20.0f)
                    .lineTo(19.0f, 9.5f).moveTo(9.5f, 20.0f).lineTo(9.5f, 14.0f)
                    .lineTo(14.5f, 14.0f).lineTo(14.5f, 20.0f).build()));
    public static final IconDefinition SETTINGS = preset("SETTINGS", stroke(
            Path.builder().moveTo(9.5f, 2.5f).lineTo(14.5f, 2.5f).lineTo(15.1f, 4.6f)
                    .lineTo(17.2f, 5.8f).lineTo(19.3f, 5.2f).lineTo(21.7f, 9.5f)
                    .lineTo(20.0f, 11.0f).lineTo(20.0f, 13.0f).lineTo(21.7f, 14.5f)
                    .lineTo(19.3f, 18.8f).lineTo(17.2f, 18.2f).lineTo(15.1f, 19.4f)
                    .lineTo(14.5f, 21.5f).lineTo(9.5f, 21.5f).lineTo(8.9f, 19.4f)
                    .lineTo(6.8f, 18.2f).lineTo(4.7f, 18.8f).lineTo(2.3f, 14.5f)
                    .lineTo(4.0f, 13.0f).lineTo(4.0f, 11.0f).lineTo(2.3f, 9.5f)
                    .lineTo(4.7f, 5.2f).lineTo(6.8f, 5.8f).lineTo(8.9f, 4.6f).close().build(),
            circle(12.0f, 12.0f, 3.0f)));
    public static final IconDefinition USER = preset("USER", stroke(
            circle(12.0f, 8.0f, 4.0f),
            Path.builder().moveTo(4.0f, 21.0f).bezierTo(4.8f, 16.8f, 7.5f, 14.5f, 12.0f, 14.5f)
                    .bezierTo(16.5f, 14.5f, 19.2f, 16.8f, 20.0f, 21.0f).build()));
    public static final IconDefinition USERS = preset("USERS", stroke(
            circle(9.0f, 8.0f, 3.5f),
            Path.builder().moveTo(2.5f, 20.0f).bezierTo(3.3f, 16.2f, 5.5f, 14.0f, 9.0f, 14.0f)
                    .bezierTo(12.5f, 14.0f, 14.7f, 16.2f, 15.5f, 20.0f)
                    .moveTo(15.5f, 5.0f).bezierTo(18.3f, 5.5f, 19.2f, 9.0f, 17.0f, 10.8f)
                    .moveTo(17.0f, 14.5f).bezierTo(19.5f, 15.2f, 21.0f, 17.0f, 21.5f, 20.0f).build()));
    public static final IconDefinition EDIT = preset("EDIT", stroke(
            Path.builder().moveTo(4.0f, 20.0f).lineTo(8.5f, 19.0f).lineTo(19.5f, 8.0f)
                    .lineTo(16.0f, 4.5f).lineTo(5.0f, 15.5f).close()
                    .moveTo(14.5f, 6.0f).lineTo(18.0f, 9.5f).build()));
    public static final IconDefinition TRASH = preset("TRASH", stroke(
            Path.builder().moveTo(4.0f, 6.0f).lineTo(20.0f, 6.0f)
                    .moveTo(9.0f, 3.0f).lineTo(15.0f, 3.0f).lineTo(16.0f, 6.0f)
                    .moveTo(6.0f, 6.0f).lineTo(7.0f, 21.0f).lineTo(17.0f, 21.0f)
                    .lineTo(18.0f, 6.0f).moveTo(10.0f, 10.0f).lineTo(10.0f, 17.0f)
                    .moveTo(14.0f, 10.0f).lineTo(14.0f, 17.0f).build()));
    public static final IconDefinition SAVE = preset("SAVE", stroke(
            roundedRect(3.0f, 3.0f, 18.0f, 18.0f, 2.0f),
            Path.builder().rect(new Rect(7.0f, 3.0f, 9.0f, 6.0f))
                    .moveTo(7.0f, 21.0f).lineTo(7.0f, 14.0f).lineTo(17.0f, 14.0f)
                    .lineTo(17.0f, 21.0f).build()));
    public static final IconDefinition COPY = preset("COPY", stroke(
            roundedRect(8.0f, 8.0f, 12.0f, 12.0f, 2.0f),
            Path.builder().moveTo(16.0f, 8.0f).lineTo(16.0f, 6.0f).quadTo(16.0f, 4.0f, 14.0f, 4.0f)
                    .lineTo(6.0f, 4.0f).quadTo(4.0f, 4.0f, 4.0f, 6.0f)
                    .lineTo(4.0f, 14.0f).quadTo(4.0f, 16.0f, 6.0f, 16.0f).lineTo(8.0f, 16.0f).build()));
    public static final IconDefinition DOWNLOAD = preset("DOWNLOAD", stroke(
            Path.builder().moveTo(12.0f, 3.0f).lineTo(12.0f, 15.0f)
                    .moveTo(7.0f, 10.0f).lineTo(12.0f, 15.0f).lineTo(17.0f, 10.0f)
                    .moveTo(4.0f, 20.0f).lineTo(20.0f, 20.0f).build()));
    public static final IconDefinition UPLOAD = preset("UPLOAD", stroke(
            Path.builder().moveTo(12.0f, 16.0f).lineTo(12.0f, 4.0f)
                    .moveTo(7.0f, 9.0f).lineTo(12.0f, 4.0f).lineTo(17.0f, 9.0f)
                    .moveTo(4.0f, 20.0f).lineTo(20.0f, 20.0f).build()));
    public static final IconDefinition REFRESH = preset("REFRESH", stroke(
            Path.builder().arc(12.0f, 12.0f, 8.0f, (float) (-Math.PI * 0.8), (float) (Math.PI * 0.45),
                            ArcDirection.CLOCKWISE)
                    .moveTo(18.8f, 5.2f).lineTo(19.8f, 10.0f).lineTo(15.0f, 9.0f)
                    .arc(12.0f, 12.0f, 8.0f, (float) (Math.PI * 0.2), (float) (Math.PI * 1.45),
                            ArcDirection.CLOCKWISE)
                    .moveTo(5.2f, 18.8f).lineTo(4.2f, 14.0f).lineTo(9.0f, 15.0f).build()));
    public static final IconDefinition UNDO = preset("UNDO", stroke(
            Path.builder().moveTo(9.0f, 7.0f).lineTo(4.0f, 12.0f).lineTo(9.0f, 17.0f)
                    .moveTo(4.0f, 12.0f).lineTo(13.0f, 12.0f)
                    .bezierTo(17.5f, 12.0f, 20.0f, 14.5f, 20.0f, 19.0f).build()));
    public static final IconDefinition REDO = preset("REDO", stroke(
            Path.builder().moveTo(15.0f, 7.0f).lineTo(20.0f, 12.0f).lineTo(15.0f, 17.0f)
                    .moveTo(20.0f, 12.0f).lineTo(11.0f, 12.0f)
                    .bezierTo(6.5f, 12.0f, 4.0f, 14.5f, 4.0f, 19.0f).build()));
    public static final IconDefinition EYE = preset("EYE", stroke(
            Path.builder().moveTo(2.5f, 12.0f).bezierTo(5.0f, 7.0f, 8.0f, 5.0f, 12.0f, 5.0f)
                    .bezierTo(16.0f, 5.0f, 19.0f, 7.0f, 21.5f, 12.0f)
                    .bezierTo(19.0f, 17.0f, 16.0f, 19.0f, 12.0f, 19.0f)
                    .bezierTo(8.0f, 19.0f, 5.0f, 17.0f, 2.5f, 12.0f).close().build(),
            circle(12.0f, 12.0f, 3.0f)));
    public static final IconDefinition EYE_OFF = preset("EYE_OFF", stroke(
            Path.builder().moveTo(3.0f, 3.0f).lineTo(21.0f, 21.0f)
                    .moveTo(6.0f, 6.8f).bezierTo(4.6f, 8.0f, 3.4f, 9.8f, 2.5f, 12.0f)
                    .bezierTo(5.0f, 17.0f, 8.0f, 19.0f, 12.0f, 19.0f)
                    .bezierTo(13.8f, 19.0f, 15.4f, 18.6f, 16.8f, 17.8f)
                    .moveTo(9.2f, 5.4f).bezierTo(10.1f, 5.1f, 11.0f, 5.0f, 12.0f, 5.0f)
                    .bezierTo(16.0f, 5.0f, 19.0f, 7.0f, 21.5f, 12.0f)
                    .moveTo(9.9f, 9.9f).bezierTo(8.8f, 11.0f, 8.8f, 13.0f, 10.1f, 14.1f)
                    .bezierTo(11.2f, 15.2f, 13.0f, 15.2f, 14.1f, 14.1f).build()));
    public static final IconDefinition LOCK = preset("LOCK", stroke(
            roundedRect(5.0f, 10.0f, 14.0f, 11.0f, 2.0f),
            Path.builder().moveTo(8.0f, 10.0f).lineTo(8.0f, 7.0f)
                    .arc(12.0f, 7.0f, 4.0f, (float) Math.PI, TAU, ArcDirection.CLOCKWISE)
                    .lineTo(16.0f, 10.0f).build()));
    public static final IconDefinition UNLOCK = preset("UNLOCK", stroke(
            roundedRect(5.0f, 10.0f, 14.0f, 11.0f, 2.0f),
            Path.builder().moveTo(16.0f, 10.0f).lineTo(16.0f, 7.0f)
                    .arc(12.0f, 7.0f, 4.0f, 0.0f, (float) -Math.PI, ArcDirection.COUNTER_CLOCKWISE)
                    .lineTo(8.0f, 8.0f).build()));
    public static final IconDefinition STAR = preset("STAR", stroke(
            Path.builder().moveTo(12.0f, 2.5f).lineTo(14.9f, 8.6f).lineTo(21.5f, 9.4f)
                    .lineTo(16.7f, 14.0f).lineTo(18.0f, 20.5f).lineTo(12.0f, 17.2f)
                    .lineTo(6.0f, 20.5f).lineTo(7.3f, 14.0f).lineTo(2.5f, 9.4f)
                    .lineTo(9.1f, 8.6f).close().build()));
    public static final IconDefinition HEART = preset("HEART", stroke(
            Path.builder().moveTo(12.0f, 20.5f).lineTo(4.2f, 13.0f)
                    .bezierTo(0.2f, 9.2f, 2.4f, 3.5f, 7.3f, 3.5f)
                    .bezierTo(9.5f, 3.5f, 11.0f, 4.8f, 12.0f, 6.3f)
                    .bezierTo(13.0f, 4.8f, 14.5f, 3.5f, 16.7f, 3.5f)
                    .bezierTo(21.6f, 3.5f, 23.8f, 9.2f, 19.8f, 13.0f).close().build()));
    public static final IconDefinition BELL = preset("BELL", stroke(
            Path.builder().moveTo(5.0f, 17.0f).lineTo(7.0f, 14.5f).lineTo(7.0f, 10.0f)
                    .bezierTo(7.0f, 6.8f, 9.0f, 4.5f, 12.0f, 4.5f)
                    .bezierTo(15.0f, 4.5f, 17.0f, 6.8f, 17.0f, 10.0f)
                    .lineTo(17.0f, 14.5f).lineTo(19.0f, 17.0f).close()
                    .moveTo(10.0f, 20.0f).quadTo(12.0f, 22.0f, 14.0f, 20.0f).build()));
    public static final IconDefinition CALENDAR = preset("CALENDAR", stroke(
            roundedRect(3.0f, 5.0f, 18.0f, 16.0f, 2.0f),
            Path.builder().moveTo(3.0f, 10.0f).lineTo(21.0f, 10.0f)
                    .moveTo(8.0f, 3.0f).lineTo(8.0f, 7.0f)
                    .moveTo(16.0f, 3.0f).lineTo(16.0f, 7.0f).build()));
    public static final IconDefinition CLOCK = preset("CLOCK", stroke(
            circle(12.0f, 12.0f, 9.0f),
            Path.builder().moveTo(12.0f, 7.0f).lineTo(12.0f, 12.0f).lineTo(16.0f, 14.0f).build()));
    public static final IconDefinition FOLDER = preset("FOLDER", stroke(
            Path.builder().moveTo(3.0f, 6.0f).quadTo(3.0f, 4.0f, 5.0f, 4.0f)
                    .lineTo(10.0f, 4.0f).lineTo(12.0f, 7.0f).lineTo(19.0f, 7.0f)
                    .quadTo(21.0f, 7.0f, 21.0f, 9.0f).lineTo(20.0f, 19.0f)
                    .quadTo(20.0f, 21.0f, 18.0f, 21.0f).lineTo(5.0f, 21.0f)
                    .quadTo(3.0f, 21.0f, 3.0f, 19.0f).close().build()));
    public static final IconDefinition FILE = preset("FILE", stroke(
            Path.builder().moveTo(6.0f, 2.5f).lineTo(14.0f, 2.5f).lineTo(19.0f, 7.5f)
                    .lineTo(19.0f, 21.5f).lineTo(6.0f, 21.5f).close()
                    .moveTo(14.0f, 2.5f).lineTo(14.0f, 7.5f).lineTo(19.0f, 7.5f).build()));
    public static final IconDefinition IMAGE = preset("IMAGE", stroke(
            roundedRect(3.0f, 4.0f, 18.0f, 16.0f, 2.0f),
            circle(8.0f, 9.0f, 2.0f),
            Path.builder().moveTo(4.0f, 18.0f).lineTo(9.0f, 13.0f).lineTo(12.0f, 16.0f)
                    .lineTo(15.0f, 12.5f).lineTo(20.0f, 18.0f).build()));
    public static final IconDefinition LINK = preset("LINK", stroke(
            Path.builder().moveTo(9.5f, 14.5f).lineTo(14.5f, 9.5f)
                    .moveTo(7.5f, 16.5f).lineTo(5.8f, 18.2f)
                    .bezierTo(3.8f, 20.2f, 0.8f, 17.2f, 2.8f, 15.2f).lineTo(6.2f, 11.8f)
                    .bezierTo(8.2f, 9.8f, 11.2f, 12.8f, 9.2f, 14.8f)
                    .moveTo(16.5f, 7.5f).lineTo(18.2f, 5.8f)
                    .bezierTo(20.2f, 3.8f, 23.2f, 6.8f, 21.2f, 8.8f).lineTo(17.8f, 12.2f)
                    .bezierTo(15.8f, 14.2f, 12.8f, 11.2f, 14.8f, 9.2f).build()));
    public static final IconDefinition EXTERNAL_LINK = preset("EXTERNAL_LINK", stroke(
            Path.builder().moveTo(13.0f, 5.0f).lineTo(5.0f, 5.0f).quadTo(3.0f, 5.0f, 3.0f, 7.0f)
                    .lineTo(3.0f, 19.0f).quadTo(3.0f, 21.0f, 5.0f, 21.0f).lineTo(17.0f, 21.0f)
                    .quadTo(19.0f, 21.0f, 19.0f, 19.0f).lineTo(19.0f, 11.0f)
                    .moveTo(12.0f, 12.0f).lineTo(21.0f, 3.0f)
                    .moveTo(15.0f, 3.0f).lineTo(21.0f, 3.0f).lineTo(21.0f, 9.0f).build()));
    public static final IconDefinition FILTER = preset("FILTER", stroke(
            Path.builder().moveTo(3.0f, 4.0f).lineTo(21.0f, 4.0f).lineTo(14.0f, 12.0f)
                    .lineTo(14.0f, 19.0f).lineTo(10.0f, 21.0f).lineTo(10.0f, 12.0f).close().build()));
    public static final IconDefinition GRID = preset("GRID", stroke(
            roundedRect(3.0f, 3.0f, 7.0f, 7.0f, 1.0f),
            roundedRect(14.0f, 3.0f, 7.0f, 7.0f, 1.0f),
            roundedRect(3.0f, 14.0f, 7.0f, 7.0f, 1.0f),
            roundedRect(14.0f, 14.0f, 7.0f, 7.0f, 1.0f)));
    public static final IconDefinition LIST = preset("LIST", stroke(
            Path.builder().moveTo(8.0f, 6.0f).lineTo(21.0f, 6.0f)
                    .moveTo(8.0f, 12.0f).lineTo(21.0f, 12.0f)
                    .moveTo(8.0f, 18.0f).lineTo(21.0f, 18.0f)
                    .moveTo(3.0f, 6.0f).lineTo(3.1f, 6.0f)
                    .moveTo(3.0f, 12.0f).lineTo(3.1f, 12.0f)
                    .moveTo(3.0f, 18.0f).lineTo(3.1f, 18.0f).build()));
    public static final IconDefinition MORE_HORIZONTAL = preset("MORE_HORIZONTAL", stroke(
            Path.builder().moveTo(5.0f, 12.0f).lineTo(5.1f, 12.0f)
                    .moveTo(12.0f, 12.0f).lineTo(12.1f, 12.0f)
                    .moveTo(19.0f, 12.0f).lineTo(19.1f, 12.0f).build()));
    public static final IconDefinition MORE_VERTICAL = preset("MORE_VERTICAL", stroke(
            Path.builder().moveTo(12.0f, 5.0f).lineTo(12.0f, 5.1f)
                    .moveTo(12.0f, 12.0f).lineTo(12.0f, 12.1f)
                    .moveTo(12.0f, 19.0f).lineTo(12.0f, 19.1f).build()));
    public static final IconDefinition MAXIMIZE = preset("MAXIMIZE", stroke(
            Path.builder().moveTo(4.0f, 9.0f).lineTo(4.0f, 4.0f).lineTo(9.0f, 4.0f)
                    .moveTo(15.0f, 4.0f).lineTo(20.0f, 4.0f).lineTo(20.0f, 9.0f)
                    .moveTo(20.0f, 15.0f).lineTo(20.0f, 20.0f).lineTo(15.0f, 20.0f)
                    .moveTo(9.0f, 20.0f).lineTo(4.0f, 20.0f).lineTo(4.0f, 15.0f).build()));
    public static final IconDefinition MINIMIZE = preset("MINIMIZE", stroke(
            Path.builder().moveTo(9.0f, 4.0f).lineTo(9.0f, 9.0f).lineTo(4.0f, 9.0f)
                    .moveTo(20.0f, 9.0f).lineTo(15.0f, 9.0f).lineTo(15.0f, 4.0f)
                    .moveTo(15.0f, 20.0f).lineTo(15.0f, 15.0f).lineTo(20.0f, 15.0f)
                    .moveTo(4.0f, 15.0f).lineTo(9.0f, 15.0f).lineTo(9.0f, 20.0f).build()));
    public static final IconDefinition VOLUME = preset("VOLUME", stroke(
            Path.builder().moveTo(3.0f, 10.0f).lineTo(7.0f, 10.0f).lineTo(12.0f, 5.0f)
                    .lineTo(12.0f, 19.0f).lineTo(7.0f, 14.0f).lineTo(3.0f, 14.0f).close()
                    .moveTo(15.5f, 9.0f).quadTo(18.0f, 12.0f, 15.5f, 15.0f)
                    .moveTo(18.5f, 6.0f).quadTo(23.0f, 12.0f, 18.5f, 18.0f).build()));
    public static final IconDefinition VOLUME_OFF = preset("VOLUME_OFF", stroke(
            Path.builder().moveTo(3.0f, 10.0f).lineTo(7.0f, 10.0f).lineTo(12.0f, 5.0f)
                    .lineTo(12.0f, 19.0f).lineTo(7.0f, 14.0f).lineTo(3.0f, 14.0f).close()
                    .moveTo(16.0f, 9.0f).lineTo(21.0f, 14.0f)
                    .moveTo(21.0f, 9.0f).lineTo(16.0f, 14.0f).build()));
    public static final IconDefinition HELP = preset("HELP", stroke(
            circle(12.0f, 12.0f, 9.0f),
            Path.builder().moveTo(9.5f, 9.0f).bezierTo(9.8f, 6.4f, 14.2f, 6.2f, 14.7f, 8.8f)
                    .bezierTo(15.1f, 10.8f, 12.0f, 11.2f, 12.0f, 14.0f)
                    .moveTo(12.0f, 17.5f).lineTo(12.0f, 17.6f).build()));
    public static final IconDefinition POWER = preset("POWER", stroke(
            Path.builder().arc(12.0f, 13.0f, 8.0f, (float) (-Math.PI * 0.7),
                            (float) (Math.PI * 1.2), ArcDirection.CLOCKWISE)
                    .moveTo(12.0f, 2.5f).lineTo(12.0f, 12.0f).build()));

    private static final Map<String, IconDefinition> ALL =
            Collections.unmodifiableMap(new LinkedHashMap<>(CATALOGUE));

    private Icons() {
    }

    /** Returns every preset and compatibility alias in stable declaration order. */
    public static Map<String, IconDefinition> all() {
        return ALL;
    }

    private static IconDefinition preset(String name, IconDefinition definition) {
        CATALOGUE.put(name, definition);
        return definition;
    }

    private static IconDefinition stroke(Path... paths) {
        IconDefinition.Builder builder = IconDefinition.builder(24.0f, 24.0f);
        StrokeStyle style = StrokeStyle.width(2.0f).cap(LineCap.ROUND).join(LineJoin.ROUND).build();
        for (Path path : paths) {
            builder.stroke(path, null, style);
        }
        return builder.build();
    }

    private static IconDefinition fill(Path... paths) {
        IconDefinition.Builder builder = IconDefinition.builder(24.0f, 24.0f);
        for (Path path : paths) {
            builder.fill(path, null);
        }
        return builder.build();
    }

    private static Path circle(float centerX, float centerY, float radius) {
        return Path.builder().arc(centerX, centerY, radius, 0.0f, TAU, ArcDirection.CLOCKWISE).build();
    }

    private static Path roundedRect(float x, float y, float width, float height, float radius) {
        return Path.builder()
                .roundedRect(new Rect(x, y, width, height), CornerRadii.all(radius))
                .build();
    }
}
