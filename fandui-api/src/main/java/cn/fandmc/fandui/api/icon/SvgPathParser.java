package cn.fandmc.fandui.api.icon;

import cn.fandmc.fandui.api.canvas.Path;
import cn.fandmc.fandui.api.canvas.PathBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Bounded SVG path-data parser supporting the standard M/L/H/V/C/S/Q/T/A/Z commands. */
final class SvgPathParser {
    private static final int MAX_COMMANDS = 20_000;
    private static final Pattern TOKEN = Pattern.compile(
            "[a-zA-Z]|[-+]?(?:(?:\\d+\\.?\\d*)|(?:\\.\\d+))(?:[eE][-+]?\\d+)?");

    private SvgPathParser() {
    }

    static Path parse(String value, SvgParser.Matrix transform) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("SVG path is missing d");
        }
        List<String> tokens = tokens(value);
        Cursor cursor = new Cursor(tokens);
        PathBuilder builder = Path.builder();
        char command = 0;
        float currentX = 0.0f;
        float currentY = 0.0f;
        float startX = 0.0f;
        float startY = 0.0f;
        float lastCubicX = 0.0f;
        float lastCubicY = 0.0f;
        float lastQuadX = 0.0f;
        float lastQuadY = 0.0f;
        char previous = 0;
        int commandCount = 0;
        while (cursor.hasNext()) {
            if (cursor.peekLetter()) {
                command = cursor.next().charAt(0);
            } else if (command == 0) {
                throw new IllegalArgumentException("SVG path data must begin with a command");
            }
            commandCount++;
            if (commandCount > MAX_COMMANDS) {
                throw new IllegalArgumentException("SVG path contains too many commands");
            }
            boolean relative = Character.isLowerCase(command);
            switch (Character.toUpperCase(command)) {
                case 'M' -> {
                    float x = cursor.number();
                    float y = cursor.number();
                    if (relative) {
                        x += currentX;
                        y += currentY;
                    }
                    move(builder, transform, x, y);
                    currentX = startX = x;
                    currentY = startY = y;
                    command = relative ? 'l' : 'L';
                    previous = 'M';
                }
                case 'L' -> {
                    float x = cursor.number();
                    float y = cursor.number();
                    if (relative) {
                        x += currentX;
                        y += currentY;
                    }
                    line(builder, transform, x, y);
                    currentX = x;
                    currentY = y;
                    previous = 'L';
                }
                case 'H' -> {
                    float x = cursor.number();
                    if (relative) x += currentX;
                    line(builder, transform, x, currentY);
                    currentX = x;
                    previous = 'H';
                }
                case 'V' -> {
                    float y = cursor.number();
                    if (relative) y += currentY;
                    line(builder, transform, currentX, y);
                    currentY = y;
                    previous = 'V';
                }
                case 'C' -> {
                    float c1x = cursor.number();
                    float c1y = cursor.number();
                    float c2x = cursor.number();
                    float c2y = cursor.number();
                    float x = cursor.number();
                    float y = cursor.number();
                    if (relative) {
                        c1x += currentX; c1y += currentY;
                        c2x += currentX; c2y += currentY;
                        x += currentX; y += currentY;
                    }
                    cubic(builder, transform, c1x, c1y, c2x, c2y, x, y);
                    currentX = x; currentY = y;
                    lastCubicX = c2x; lastCubicY = c2y;
                    previous = 'C';
                }
                case 'S' -> {
                    float c2x = cursor.number();
                    float c2y = cursor.number();
                    float x = cursor.number();
                    float y = cursor.number();
                    if (relative) {
                        c2x += currentX; c2y += currentY; x += currentX; y += currentY;
                    }
                    float c1x = (previous == 'C' || previous == 'S') ? 2.0f * currentX - lastCubicX : currentX;
                    float c1y = (previous == 'C' || previous == 'S') ? 2.0f * currentY - lastCubicY : currentY;
                    cubic(builder, transform, c1x, c1y, c2x, c2y, x, y);
                    currentX = x; currentY = y;
                    lastCubicX = c2x; lastCubicY = c2y;
                    previous = 'S';
                }
                case 'Q' -> {
                    float cx = cursor.number();
                    float cy = cursor.number();
                    float x = cursor.number();
                    float y = cursor.number();
                    if (relative) {
                        cx += currentX; cy += currentY; x += currentX; y += currentY;
                    }
                    quad(builder, transform, cx, cy, x, y);
                    currentX = x; currentY = y;
                    lastQuadX = cx; lastQuadY = cy;
                    previous = 'Q';
                }
                case 'T' -> {
                    float x = cursor.number();
                    float y = cursor.number();
                    if (relative) { x += currentX; y += currentY; }
                    float cx = (previous == 'Q' || previous == 'T') ? 2.0f * currentX - lastQuadX : currentX;
                    float cy = (previous == 'Q' || previous == 'T') ? 2.0f * currentY - lastQuadY : currentY;
                    quad(builder, transform, cx, cy, x, y);
                    currentX = x; currentY = y;
                    lastQuadX = cx; lastQuadY = cy;
                    previous = 'T';
                }
                case 'A' -> {
                    float rx = Math.abs(cursor.number());
                    float ry = Math.abs(cursor.number());
                    float rotation = cursor.number();
                    boolean large = cursor.number() != 0.0f;
                    boolean sweep = cursor.number() != 0.0f;
                    float x = cursor.number();
                    float y = cursor.number();
                    if (relative) { x += currentX; y += currentY; }
                    arc(builder, transform, currentX, currentY, rx, ry, rotation, large, sweep, x, y);
                    currentX = x; currentY = y;
                    previous = 'A';
                }
                case 'Z' -> {
                    builder.close();
                    currentX = startX;
                    currentY = startY;
                    previous = 'Z';
                    command = 0;
                }
                default -> throw new IllegalArgumentException("Unsupported SVG path command " + command);
            }
        }
        return builder.build();
    }

    static List<Float> numbers(String value) {
        List<Float> result = new ArrayList<>();
        if (value == null || value.isBlank()) {
            return result;
        }
        Matcher matcher = TOKEN.matcher(value);
        int end = 0;
        while (matcher.find()) {
            for (int index = end; index < matcher.start(); index++) {
                char character = value.charAt(index);
                if (!Character.isWhitespace(character) && character != ',') {
                    throw new IllegalArgumentException("Invalid SVG number separator");
                }
            }
            String token = matcher.group();
            if (Character.isLetter(token.charAt(0))) {
                throw new IllegalArgumentException("Expected a number in SVG list");
            }
            try {
                float parsed = Float.parseFloat(token);
                if (!Float.isFinite(parsed)) throw new NumberFormatException(token);
                result.add(parsed);
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException("Invalid SVG number " + token, exception);
            }
            end = matcher.end();
        }
        for (int index = end; index < value.length(); index++) {
            char character = value.charAt(index);
            if (!Character.isWhitespace(character) && character != ',') {
                throw new IllegalArgumentException("Invalid SVG number list");
            }
        }
        return List.copyOf(result);
    }

    private static List<String> tokens(String value) {
        List<String> result = new ArrayList<>();
        Matcher matcher = TOKEN.matcher(value);
        int end = 0;
        while (matcher.find()) {
            for (int index = end; index < matcher.start(); index++) {
                char character = value.charAt(index);
                if (!Character.isWhitespace(character) && character != ',') {
                    throw new IllegalArgumentException("Invalid SVG path separator");
                }
            }
            result.add(matcher.group());
            end = matcher.end();
        }
        for (int index = end; index < value.length(); index++) {
            char character = value.charAt(index);
            if (!Character.isWhitespace(character) && character != ',') {
                throw new IllegalArgumentException("Invalid SVG path token");
            }
        }
        return List.copyOf(result);
    }

    private static void move(PathBuilder builder, SvgParser.Matrix transform, float x, float y) {
        float[] point = transform.map(x, y);
        builder.moveTo(point[0], point[1]);
    }

    private static void line(PathBuilder builder, SvgParser.Matrix transform, float x, float y) {
        float[] point = transform.map(x, y);
        builder.lineTo(point[0], point[1]);
    }

    private static void quad(PathBuilder builder, SvgParser.Matrix transform,
                             float cx, float cy, float x, float y) {
        float[] control = transform.map(cx, cy);
        float[] end = transform.map(x, y);
        builder.quadTo(control[0], control[1], end[0], end[1]);
    }

    private static void cubic(PathBuilder builder, SvgParser.Matrix transform,
                              float c1x, float c1y, float c2x, float c2y, float x, float y) {
        float[] c1 = transform.map(c1x, c1y);
        float[] c2 = transform.map(c2x, c2y);
        float[] end = transform.map(x, y);
        builder.bezierTo(c1[0], c1[1], c2[0], c2[1], end[0], end[1]);
    }

    private static void arc(PathBuilder builder, SvgParser.Matrix transform,
                            float x0, float y0, float rx, float ry, float rotation,
                            boolean large, boolean sweep, float x1, float y1) {
        if (rx == 0.0f || ry == 0.0f || (Math.abs(x0 - x1) < 0.00001f && Math.abs(y0 - y1) < 0.00001f)) {
            line(builder, transform, x1, y1);
            return;
        }
        double phi = Math.toRadians(rotation % 360.0);
        double cosPhi = Math.cos(phi);
        double sinPhi = Math.sin(phi);
        double dx = (x0 - x1) * 0.5;
        double dy = (y0 - y1) * 0.5;
        double xp = cosPhi * dx + sinPhi * dy;
        double yp = -sinPhi * dx + cosPhi * dy;
        double rx2 = rx * rx;
        double ry2 = ry * ry;
        double lambda = xp * xp / rx2 + yp * yp / ry2;
        if (lambda > 1.0) {
            double scale = Math.sqrt(lambda);
            rx *= scale;
            ry *= scale;
            rx2 = rx * rx;
            ry2 = ry * ry;
        }
        double sign = large == sweep ? -1.0 : 1.0;
        double numerator = Math.max(0.0, (rx2 * ry2 - rx2 * yp * yp - ry2 * xp * xp)
                / (rx2 * yp * yp + ry2 * xp * xp));
        double coef = sign * Math.sqrt(numerator);
        double cxp = coef * (rx * yp / ry);
        double cyp = coef * (-ry * xp / rx);
        double cx = cosPhi * cxp - sinPhi * cyp + (x0 + x1) * 0.5;
        double cy = sinPhi * cxp + cosPhi * cyp + (y0 + y1) * 0.5;
        double ux = (xp - cxp) / rx;
        double uy = (yp - cyp) / ry;
        double vx = (-xp - cxp) / rx;
        double vy = (-yp - cyp) / ry;
        double start = Math.atan2(uy, ux);
        double delta = Math.atan2(ux * vy - uy * vx, ux * vx + uy * vy);
        if (!sweep && delta > 0.0) delta -= Math.PI * 2.0;
        if (sweep && delta < 0.0) delta += Math.PI * 2.0;
        int segments = Math.max(1, (int) Math.ceil(Math.abs(delta) / (Math.PI * 0.5)));
        double step = delta / segments;
        for (int index = 0; index < segments; index++) {
            double a0 = start + index * step;
            double a1 = a0 + step;
            double alpha = 4.0 / 3.0 * Math.tan((a1 - a0) / 4.0);
            double cos0 = Math.cos(a0);
            double sin0 = Math.sin(a0);
            double cos1 = Math.cos(a1);
            double sin1 = Math.sin(a1);
            double c1x = cosPhi * (rx * (cos0 - alpha * sin0)) - sinPhi * (ry * (sin0 + alpha * cos0)) + cx;
            double c1y = sinPhi * (rx * (cos0 - alpha * sin0)) + cosPhi * (ry * (sin0 + alpha * cos0)) + cy;
            double c2x = cosPhi * (rx * (cos1 + alpha * sin1)) - sinPhi * (ry * (sin1 - alpha * cos1)) + cx;
            double c2y = sinPhi * (rx * (cos1 + alpha * sin1)) + cosPhi * (ry * (sin1 - alpha * cos1)) + cy;
            double ex = cosPhi * (rx * cos1) - sinPhi * (ry * sin1) + cx;
            double ey = sinPhi * (rx * cos1) + cosPhi * (ry * sin1) + cy;
            cubic(builder, transform, (float) c1x, (float) c1y, (float) c2x, (float) c2y,
                    (float) ex, (float) ey);
        }
    }

    private static final class Cursor {
        private final List<String> tokens;
        private int index;

        private Cursor(List<String> tokens) {
            this.tokens = tokens;
        }

        private boolean hasNext() {
            return index < tokens.size();
        }

        private boolean peekLetter() {
            return hasNext() && Character.isLetter(tokens.get(index).charAt(0));
        }

        private String next() {
            if (!hasNext()) throw new IllegalArgumentException("Unexpected end of SVG path");
            return tokens.get(index++);
        }

        private float number() {
            String token = next();
            if (Character.isLetter(token.charAt(0))) {
                throw new IllegalArgumentException("SVG path command has too few arguments");
            }
            return Float.parseFloat(token);
        }
    }
}
