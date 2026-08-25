package cn.fandmc.fandui.api.icon;

import cn.fandmc.fandui.api.canvas.ArcDirection;
import cn.fandmc.fandui.api.canvas.LineCap;
import cn.fandmc.fandui.api.canvas.LineJoin;
import cn.fandmc.fandui.api.canvas.Path;
import cn.fandmc.fandui.api.canvas.PathBuilder;
import cn.fandmc.fandui.api.canvas.StrokeStyle;
import cn.fandmc.fandui.api.style.Color;
import cn.fandmc.fandui.api.style.Paint;
import cn.fandmc.fandui.api.style.SolidPaint;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Secure DOM adapter for the supported SVG geometry subset. */
final class SvgParser {
    private static final int MAX_SOURCE_CHARS = 2 * 1024 * 1024;
    private static final int MAX_LAYERS = 4096;
    private static final Map<String, Color> NAMED_COLORS = namedColors();

    private SvgParser() {
    }

    static IconDefinition parse(String source) {
        if (source == null || source.isBlank()) {
            throw new IllegalArgumentException("SVG source must not be empty");
        }
        if (source.length() > MAX_SOURCE_CHARS) {
            throw new IllegalArgumentException("SVG source exceeds the 2 MiB limit");
        }
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            Element root = factory.newDocumentBuilder()
                    .parse(new InputSource(new StringReader(source)))
                    .getDocumentElement();
            if (root == null || !"svg".equalsIgnoreCase(root.getTagName())) {
                throw new IllegalArgumentException("SVG root element must be <svg>");
            }
            float[] viewBox = viewBox(root);
            List<IconDefinition.Layer> layers = new ArrayList<>();
            // Normalize a non-zero SVG viewBox origin so every public path uses a
            // stable local coordinate system starting at (0, 0).
            walk(root, StyleState.defaults(), Matrix.translation(-viewBox[0], -viewBox[1]), layers);
            if (layers.isEmpty()) {
                throw new IllegalArgumentException("SVG contains no supported drawable elements");
            }
            return IconDefinition.builder(viewBox[2], viewBox[3])
                    .layers(layers)
                    .build();
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("Invalid SVG source", exception);
        }
    }

    private static void walk(
            Element element,
            StyleState inherited,
            Matrix parentTransform,
            List<IconDefinition.Layer> layers) {
        String tag = element.getTagName().toLowerCase(Locale.ROOT);
        if (tag.equals("defs") || tag.equals("metadata") || tag.equals("title")
                || tag.equals("desc") || tag.equals("style")) {
            return;
        }
        StyleState style = inherited.merge(element);
        Matrix transform = parentTransform.multiply(parseTransform(attribute(element, "transform")));
        switch (tag) {
            case "svg", "g", "symbol" -> children(element, style, transform, layers);
            case "path" -> add(layers, SvgPathParser.parse(attribute(element, "d"), transform), style);
            case "rect" -> add(layers, rectangle(element, transform), style);
            case "circle" -> add(layers, ellipse(element, transform, false), style);
            case "ellipse" -> add(layers, ellipse(element, transform, true), style);
            case "line" -> add(layers, line(element, transform), style);
            case "polyline", "polygon" -> add(layers, polyline(element, transform, tag.equals("polygon")), style);
            default -> {
                // Unsupported presentation-only elements are ignored; drawable elements fail clearly.
                if (element.hasAttribute("d") || tag.equals("image") || tag.equals("use")) {
                    throw new IllegalArgumentException("Unsupported SVG element <" + tag + ">");
                }
                children(element, style, transform, layers);
            }
        }
    }

    private static void children(
            Element element,
            StyleState style,
            Matrix transform,
            List<IconDefinition.Layer> layers) {
        NodeList nodes = element.getChildNodes();
        for (int index = 0; index < nodes.getLength(); index++) {
            Node node = nodes.item(index);
            if (node instanceof Element child) {
                walk(child, style, transform, layers);
            }
        }
    }

    private static void add(List<IconDefinition.Layer> layers, Path path, StyleState style) {
        if (path == null || path.bounds().width() == 0.0f && path.bounds().height() == 0.0f) {
            return;
        }
        if (layers.size() >= MAX_LAYERS) {
            throw new IllegalArgumentException("SVG contains too many drawable elements");
        }
        Paint fill = style.resolveFill();
        Paint stroke = style.resolveStroke();
        if (fill == null && stroke == null) {
            return;
        }
        StrokeStyle strokeStyle = stroke == null ? null : StrokeStyle.width(style.strokeWidth)
                .cap(style.lineCap)
                .join(style.lineJoin)
                .build();
        layers.add(new IconDefinition.Layer(path, fill, stroke, strokeStyle));
    }

    private static Path rectangle(Element element, Matrix transform) {
        float x = number(element, "x", 0.0f);
        float y = number(element, "y", 0.0f);
        float width = number(element, "width", -1.0f);
        float height = number(element, "height", -1.0f);
        if (width <= 0.0f || height <= 0.0f) {
            throw new IllegalArgumentException("SVG rect dimensions must be positive");
        }
        String rxValue = attribute(element, "rx");
        String ryValue = attribute(element, "ry");
        float rx = rxValue.isEmpty() ? (ryValue.isEmpty() ? 0.0f : number(element, "ry", 0.0f))
                : number(element, "rx", 0.0f);
        float ry = ryValue.isEmpty() ? rx : number(element, "ry", rx);
        rx = Math.min(Math.max(0.0f, rx), width * 0.5f);
        ry = Math.min(Math.max(0.0f, ry), height * 0.5f);
        return roundedRectangle(x, y, width, height, rx, ry, transform);
    }

    private static Path ellipse(Element element, Matrix transform, boolean ellipse) {
        float cx = number(element, "cx", 0.0f);
        float cy = number(element, "cy", 0.0f);
        float rx = number(element, ellipse ? "rx" : "r", -1.0f);
        float ry = number(element, ellipse ? "ry" : "r", -1.0f);
        if (rx <= 0.0f || ry <= 0.0f) {
            throw new IllegalArgumentException("SVG ellipse radii must be positive");
        }
        // Four cubic segments are stable across all Canvas backends and preserve vector scaling.
        float k = 0.5522847498f;
        var builder = Path.builder();
        point(builder, transform, cx + rx, cy);
        cubic(builder, transform, cx + rx, cy + k * ry, cx + k * rx, cy + ry, cx, cy + ry);
        cubic(builder, transform, cx - k * rx, cy + ry, cx - rx, cy + k * ry, cx - rx, cy);
        cubic(builder, transform, cx - rx, cy - k * ry, cx - k * rx, cy - ry, cx, cy - ry);
        cubic(builder, transform, cx + k * rx, cy - ry, cx + rx, cy - k * ry, cx + rx, cy);
        return builder.close().build();
    }

    private static Path line(Element element, Matrix transform) {
        var builder = Path.builder();
        point(builder, transform, number(element, "x1", 0.0f), number(element, "y1", 0.0f));
        pointLine(builder, transform, number(element, "x2", 0.0f), number(element, "y2", 0.0f));
        return builder.build();
    }

    private static Path polyline(Element element, Matrix transform, boolean close) {
        String value = attribute(element, "points");
        List<Float> numbers = SvgPathParser.numbers(value);
        if (numbers.size() < 4 || numbers.size() % 2 != 0) {
            throw new IllegalArgumentException("SVG points must contain pairs");
        }
        var builder = Path.builder();
        point(builder, transform, numbers.get(0), numbers.get(1));
        for (int index = 2; index < numbers.size(); index += 2) {
            pointLine(builder, transform, numbers.get(index), numbers.get(index + 1));
        }
        if (close) {
            builder.close();
        }
        return builder.build();
    }

    private static void cubic(PathBuilder builder, Matrix transform,
                              float c1x, float c1y, float c2x, float c2y, float x, float y) {
        float[] c1 = transform.map(c1x, c1y);
        float[] c2 = transform.map(c2x, c2y);
        float[] end = transform.map(x, y);
        builder.bezierTo(c1[0], c1[1], c2[0], c2[1], end[0], end[1]);
    }

    private static void point(PathBuilder builder, Matrix transform, float x, float y) {
        float[] p = transform.map(x, y);
        builder.moveTo(p[0], p[1]);
    }

    private static void pointLine(PathBuilder builder, Matrix transform, float x, float y) {
        float[] p = transform.map(x, y);
        builder.lineTo(p[0], p[1]);
    }

    private static Path roundedRectangle(
            float x,
            float y,
            float width,
            float height,
            float rx,
            float ry,
            Matrix transform) {
        // SVG rect corner radii are elliptical. Cubic segments retain the
        // shape under arbitrary transforms instead of collapsing it to an AABB.
        float k = 0.55228475f;
        PathBuilder builder = Path.builder();
        point(builder, transform, x + rx, y);
        pointLine(builder, transform, x + width - rx, y);
        cubic(builder, transform,
                x + width - rx + k * rx, y,
                x + width, y + ry - k * ry,
                x + width, y + ry);
        pointLine(builder, transform, x + width, y + height - ry);
        cubic(builder, transform,
                x + width, y + height - ry + k * ry,
                x + width - rx + k * rx, y + height,
                x + width - rx, y + height);
        pointLine(builder, transform, x + rx, y + height);
        cubic(builder, transform,
                x + rx - k * rx, y + height,
                x, y + height - ry + k * ry,
                x, y + height - ry);
        pointLine(builder, transform, x, y + ry);
        cubic(builder, transform,
                x, y + ry - k * ry,
                x + rx - k * rx, y,
                x + rx, y);
        return builder.close().build();
    }

    private static float[] viewBox(Element root) {
        String value = attribute(root, "viewBox");
        List<Float> values = SvgPathParser.numbers(value);
        if (values.size() == 4 && values.get(2) > 0.0f && values.get(3) > 0.0f) {
            return new float[]{values.get(0), values.get(1), values.get(2), values.get(3)};
        }
        float width = number(root, "width", 24.0f);
        float height = number(root, "height", 24.0f);
        if (width <= 0.0f || height <= 0.0f) {
            throw new IllegalArgumentException("SVG viewBox dimensions must be positive");
        }
        return new float[]{0.0f, 0.0f, width, height};
    }

    private static String attribute(Element element, String name) {
        String value = element.getAttribute(name);
        return value == null ? "" : value.trim();
    }

    private static float number(Element element, String name, float fallback) {
        String value = attribute(element, name);
        if (value.isEmpty()) {
            return fallback;
        }
        return length(value, name);
    }

    /** Parses a user-unit length while keeping the bounded subset deterministic. */
    private static float length(String value, String name) {
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (normalized.endsWith("px")) {
            normalized = normalized.substring(0, normalized.length() - 2).trim();
        } else if (normalized.endsWith("%") || normalized.endsWith("em")
                || normalized.endsWith("rem") || normalized.endsWith("pt")
                || normalized.endsWith("pc") || normalized.endsWith("cm")
                || normalized.endsWith("mm") || normalized.endsWith("in")) {
            throw new IllegalArgumentException("Unsupported SVG length unit for " + name);
        }
        try {
            float parsed = Float.parseFloat(normalized);
            if (!Float.isFinite(parsed)) {
                throw new NumberFormatException(normalized);
            }
            return parsed;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Invalid SVG number for " + name, exception);
        }
    }

    private static Matrix parseTransform(String value) {
        if (value == null || value.isBlank()) {
            return Matrix.identity();
        }
        Matrix result = Matrix.identity();
        int offset = 0;
        while (offset < value.length()) {
            while (offset < value.length() && (Character.isWhitespace(value.charAt(offset)) || value.charAt(offset) == ',')) {
                offset++;
            }
            int start = offset;
            while (offset < value.length() && Character.isLetter(value.charAt(offset))) {
                offset++;
            }
            if (start == offset) {
                throw new IllegalArgumentException("Invalid SVG transform");
            }
            String name = value.substring(start, offset).toLowerCase(Locale.ROOT);
            int open = value.indexOf('(', offset);
            int close = value.indexOf(')', open + 1);
            if (open < 0 || close < 0) {
                throw new IllegalArgumentException("Invalid SVG transform");
            }
            List<Float> numbers = SvgPathParser.numbers(value.substring(open + 1, close));
            Matrix next = switch (name) {
                case "translate" -> {
                    requireCount(numbers, 1, 2, name);
                    yield new Matrix(1, 0, 0, 1,
                            require(numbers, 0), numbers.size() > 1 ? numbers.get(1) : 0.0f);
                }
                case "scale" -> {
                    requireCount(numbers, 1, 2, name);
                    float sx = require(numbers, 0);
                    yield new Matrix(sx, 0, 0, numbers.size() > 1 ? numbers.get(1) : sx, 0, 0);
                }
                case "rotate" -> {
                    requireCount(numbers, 1, 3, name);
                    float radians = (float) Math.toRadians(require(numbers, 0));
                    float cos = (float) Math.cos(radians);
                    float sin = (float) Math.sin(radians);
                    Matrix rotation = new Matrix(cos, sin, -sin, cos, 0, 0);
                    if (numbers.size() >= 3) {
                        float cx = numbers.get(1);
                        float cy = numbers.get(2);
                        yield Matrix.translation(cx, cy).multiply(rotation)
                                .multiply(Matrix.translation(-cx, -cy));
                    }
                    yield rotation;
                }
                case "matrix" -> {
                    requireCount(numbers, 6, 6, name);
                    yield new Matrix(require(numbers, 0), require(numbers, 1), require(numbers, 2),
                            require(numbers, 3), require(numbers, 4), require(numbers, 5));
                }
                default -> throw new IllegalArgumentException("Unsupported SVG transform " + name);
            };
            result = result.multiply(next);
            offset = close + 1;
        }
        return result;
    }

    private static float require(List<Float> values, int index) {
        if (index >= values.size()) {
            throw new IllegalArgumentException("SVG transform has too few arguments");
        }
        return values.get(index);
    }

    private static void requireCount(List<Float> values, int minimum, int maximum, String name) {
        if (values.size() < minimum || values.size() > maximum) {
            throw new IllegalArgumentException("SVG " + name + " transform has an invalid argument count");
        }
    }

    static record Matrix(float a, float b, float c, float d, float e, float f) {
        static Matrix identity() {
            return new Matrix(1, 0, 0, 1, 0, 0);
        }

        static Matrix translation(float x, float y) {
            return new Matrix(1, 0, 0, 1, x, y);
        }

        Matrix multiply(Matrix other) {
            return new Matrix(
                    a * other.a + c * other.b,
                    b * other.a + d * other.b,
                    a * other.c + c * other.d,
                    b * other.c + d * other.d,
                    a * other.e + c * other.f + e,
                    b * other.e + d * other.f + f);
        }

        float[] map(float x, float y) {
            return new float[]{a * x + c * y + e, b * x + d * y + f};
        }
    }

    private static final class StyleState {
        private final Paint fill;
        private final Paint stroke;
        private final float fillOpacity;
        private final float strokeOpacity;
        private final float strokeWidth;
        private final LineCap lineCap;
        private final LineJoin lineJoin;
        private final float opacity;

        private StyleState(Paint fill, Paint stroke, float fillOpacity, float strokeOpacity,
                           float strokeWidth, LineCap lineCap, LineJoin lineJoin, float opacity) {
            this.fill = fill;
            this.stroke = stroke;
            this.fillOpacity = fillOpacity;
            this.strokeOpacity = strokeOpacity;
            this.strokeWidth = strokeWidth;
            this.lineCap = lineCap;
            this.lineJoin = lineJoin;
            this.opacity = opacity;
        }

        private static StyleState defaults() {
            return new StyleState(new SolidPaint(Color.rgb(0x000000)), null,
                    1.0f, 1.0f, 1.0f, LineCap.BUTT, LineJoin.MITER, 1.0f);
        }

        private StyleState merge(Element element) {
            Map<String, String> css = styleAttribute(element);
            String fillValue = value(element, css, "fill");
            String strokeValue = value(element, css, "stroke");
            float nextOpacity = opacity(element, css, "opacity", 1.0f);
            float nextFillOpacity = opacity(element, css, "fill-opacity", fillOpacity);
            float nextStrokeOpacity = opacity(element, css, "stroke-opacity", strokeOpacity);
            Paint nextFill = fillValue == null ? fill : paint(fillValue, 1.0f);
            Paint nextStroke = strokeValue == null ? stroke : paint(strokeValue, 1.0f);
            String widthValue = value(element, css, "stroke-width");
            float nextWidth = widthValue == null ? strokeWidth : length(widthValue, "stroke-width");
            LineCap cap = lineCap(value(element, css, "stroke-linecap"), lineCap);
            LineJoin join = lineJoin(value(element, css, "stroke-linejoin"), lineJoin);
            return new StyleState(nextFill, nextStroke, nextFillOpacity, nextStrokeOpacity,
                    Math.max(0.001f, nextWidth), cap, join, clamp(opacity * nextOpacity));
        }

        private Paint resolveFill() {
            return withAlpha(fill, fillOpacity * opacity);
        }

        private Paint resolveStroke() {
            return withAlpha(stroke, strokeOpacity * opacity);
        }

        private static Paint withAlpha(Paint paint, float alpha) {
            if (paint == null) {
                return null;
            }
            if (!(paint instanceof SolidPaint solid)) {
                return paint;
            }
            Color color = solid.color();
            return new SolidPaint(color.withAlpha(color.alpha() * clamp(alpha)));
        }

        private static Paint paint(String value, float alpha) {
            if (value == null || value.isBlank() || value.equalsIgnoreCase("none")) {
                return null;
            }
            String normalized = value.trim().toLowerCase(Locale.ROOT);
            Color color;
            if (normalized.startsWith("#")) {
                String hex = normalized.substring(1);
                if (hex.length() == 3) {
                    hex = "" + hex.charAt(0) + hex.charAt(0) + hex.charAt(1) + hex.charAt(1)
                            + hex.charAt(2) + hex.charAt(2);
                } else if (hex.length() == 4) {
                    hex = "" + hex.charAt(0) + hex.charAt(0) + hex.charAt(1) + hex.charAt(1)
                            + hex.charAt(2) + hex.charAt(2) + hex.charAt(3) + hex.charAt(3);
                }
                if (hex.length() != 6 && hex.length() != 8) {
                    throw new IllegalArgumentException("Unsupported SVG color " + value);
                }
                long parsed;
                try {
                    parsed = Long.parseLong(hex, 16);
                } catch (NumberFormatException exception) {
                    throw new IllegalArgumentException("Unsupported SVG color " + value, exception);
                }
                color = hex.length() == 8
                        ? new Color(((parsed >>> 24) & 0xff) / 255.0f,
                        ((parsed >>> 16) & 0xff) / 255.0f,
                        ((parsed >>> 8) & 0xff) / 255.0f,
                        (parsed & 0xff) / 255.0f)
                        : Color.rgb((int) parsed);
            } else if (normalized.startsWith("rgb(") || normalized.startsWith("rgba(")) {
                int open = normalized.indexOf('(');
                int close = normalized.lastIndexOf(')');
                if (close <= open) {
                    throw new IllegalArgumentException("Unsupported SVG rgb color " + value);
                }
                List<Float> channels = SvgPathParser.numbers(normalized.substring(open + 1, close));
                if (channels.size() < 3 || channels.size() > 4) {
                    throw new IllegalArgumentException("Unsupported SVG rgb color " + value);
                }
                float rgbAlpha = channels.size() == 4 ? channels.get(3) : 255.0f;
                // CSS rgba alpha may be expressed as either [0,1] or an old-style byte.
                if (rgbAlpha > 1.0f) {
                    rgbAlpha /= 255.0f;
                }
                color = new Color(clamp(channels.get(0) / 255.0f),
                        clamp(channels.get(1) / 255.0f),
                        clamp(channels.get(2) / 255.0f), clamp(rgbAlpha));
            } else {
                color = NAMED_COLORS.get(normalized);
                if (color == null) {
                    throw new IllegalArgumentException("Unsupported SVG color " + value);
                }
            }
            return new SolidPaint(color.withAlpha(color.alpha() * clamp(alpha)));
        }

        private static Map<String, String> styleAttribute(Element element) {
            Map<String, String> values = new HashMap<>();
            String style = element.getAttribute("style");
            if (style == null || style.isBlank()) {
                return values;
            }
            for (String declaration : style.split(";")) {
                int colon = declaration.indexOf(':');
                if (colon > 0) {
                    values.put(declaration.substring(0, colon).trim().toLowerCase(Locale.ROOT),
                            declaration.substring(colon + 1).trim());
                }
            }
            return values;
        }

        private static String value(Element element, Map<String, String> css, String name) {
            String attribute = element.getAttribute(name);
            return attribute == null || attribute.isBlank() ? css.get(name) : attribute.trim();
        }

        private static float opacity(Element element, Map<String, String> css, String name, float fallback) {
            String value = value(element, css, name);
            if (value == null || value.isBlank()) {
                return fallback;
            }
            try {
                return clamp(Float.parseFloat(value));
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException("Invalid SVG opacity " + value, exception);
            }
        }

        private static LineCap lineCap(String value, LineCap fallback) {
            if (value == null) return fallback;
            return switch (value.toLowerCase(Locale.ROOT)) {
                case "round" -> LineCap.ROUND;
                case "square" -> LineCap.SQUARE;
                case "butt" -> LineCap.BUTT;
                default -> fallback;
            };
        }

        private static LineJoin lineJoin(String value, LineJoin fallback) {
            if (value == null) return fallback;
            return switch (value.toLowerCase(Locale.ROOT)) {
                case "round" -> LineJoin.ROUND;
                case "bevel" -> LineJoin.BEVEL;
                case "miter" -> LineJoin.MITER;
                default -> fallback;
            };
        }

        private static float clamp(float value) {
            return Math.max(0.0f, Math.min(1.0f, value));
        }
    }

    private static Map<String, Color> namedColors() {
        Map<String, Color> colors = new HashMap<>();
        colors.put("black", Color.rgb(0x000000));
        colors.put("white", Color.rgb(0xffffff));
        colors.put("red", Color.rgb(0xff0000));
        colors.put("green", Color.rgb(0x008000));
        colors.put("blue", Color.rgb(0x0000ff));
        colors.put("yellow", Color.rgb(0xffff00));
        colors.put("cyan", Color.rgb(0x00ffff));
        colors.put("magenta", Color.rgb(0xff00ff));
        colors.put("gray", Color.rgb(0x808080));
        colors.put("orange", Color.rgb(0xffa500));
        colors.put("purple", Color.rgb(0x800080));
        colors.put("pink", Color.rgb(0xffc0cb));
        colors.put("brown", Color.rgb(0xa52a2a));
        colors.put("lime", Color.rgb(0x00ff00));
        colors.put("navy", Color.rgb(0x000080));
        colors.put("teal", Color.rgb(0x008080));
        colors.put("silver", Color.rgb(0xc0c0c0));
        colors.put("maroon", Color.rgb(0x800000));
        colors.put("olive", Color.rgb(0x808000));
        colors.put("rebeccapurple", Color.rgb(0x663399));
        colors.put("darkgray", Color.rgb(0xa9a9a9));
        colors.put("lightgray", Color.rgb(0xd3d3d3));
        colors.put("transparent", Color.rgb(0x000000).withAlpha(0.0f));
        return Map.copyOf(colors);
    }

}
