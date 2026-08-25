package cn.fandmc.fandui.core.resource;

import cn.fandmc.fandui.api.canvas.ArcDirection;
import cn.fandmc.fandui.api.canvas.LineCap;
import cn.fandmc.fandui.api.canvas.LineJoin;
import cn.fandmc.fandui.api.canvas.Path;
import cn.fandmc.fandui.api.canvas.PathWinding;
import cn.fandmc.fandui.api.canvas.StrokeStyle;
import cn.fandmc.fandui.api.icon.IconDefinition;
import cn.fandmc.fandui.api.layout.Rect;
import cn.fandmc.fandui.api.layout.Size;
import cn.fandmc.fandui.api.style.Color;
import cn.fandmc.fandui.api.style.CornerRadii;
import cn.fandmc.fandui.api.style.Paint;
import cn.fandmc.fandui.api.style.SolidPaint;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;

/**
 * Bounded SVG-to-RGBA rasterizer used by resource reloads.
 *
 * <p>Inline SVG icons remain vector paths. Resource SVGs are rasterized once on the
 * reload worker and then follow the same texture LRU as PNG resources.</p>
 */
final class SvgImageDecoder {
    static final int MAX_ENCODED_BYTES = 2 * 1024 * 1024;
    static final int MAX_DIMENSION = 4096;
    static final long MAX_DECODED_BYTES = 64L * 1024L * 1024L;
    private static final String CACHE_KEY_VERSION =
            "FandUI static SVG RGBA8 premultiplied v1";
    private static final double TWO_PI = Math.PI * 2.0;
    private static final double ARC_EPSILON = 0.0000001;

    private SvgImageDecoder() {
    }

    static PngImageDecoder.DecodedImage decode(byte[] encoded) throws IOException {
        if (encoded == null) {
            throw new IOException("ResourceSource returned null bytes");
        }
        if (encoded.length == 0 || encoded.length > MAX_ENCODED_BYTES) {
            throw new IOException("SVG source exceeds the encoded byte limit");
        }
        String source = decodeUtf8(encoded);
        if (!source.isEmpty() && source.charAt(0) == '\uFEFF') {
            source = source.substring(1);
        }

        IconDefinition definition;
        try {
            definition = IconDefinition.fromSvg(source);
        } catch (RuntimeException exception) {
            throw new IOException("Invalid SVG resource", exception);
        }
        Size viewBox = definition.viewBox();
        int[] dimensions = rasterDimensions(source, viewBox);
        int width = dimensions[0];
        int height = dimensions[1];
        long decodedBytes = Math.multiplyExact(Math.multiplyExact((long) width, height), 4L);
        if (decodedBytes > MAX_DECODED_BYTES) {
            throw new IOException("SVG dimensions exceed the decoded image limit");
        }

        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB_PRE);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setComposite(AlphaComposite.Clear);
            graphics.fillRect(0, 0, width, height);
            graphics.setComposite(AlphaComposite.SrcOver);
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING,
                    RenderingHints.VALUE_RENDER_QUALITY);
            graphics.scale(width / viewBox.width(), height / viewBox.height());
            for (IconDefinition.Layer layer : definition.layers()) {
                renderLayer(graphics, layer);
            }
        } catch (RuntimeException exception) {
            throw new IOException("SVG rasterization failed", exception);
        } finally {
            graphics.dispose();
        }

        byte[] pixels = ImageDecodeSupport.toPremultipliedRgba(image);
        byte[] digest = ImageDecodeSupport.digest(CACHE_KEY_VERSION, width, height, pixels);
        return new PngImageDecoder.DecodedImage(
                ImageDecodeSupport.textureKey(digest), digest, width, height, pixels);
    }

    private static String decodeUtf8(byte[] encoded) throws IOException {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(encoded))
                    .toString();
        } catch (CharacterCodingException exception) {
            throw new IOException("SVG source is not valid UTF-8", exception);
        }
    }

    private static int rasterDimension(float value, String name) throws IOException {
        if (!Float.isFinite(value) || value <= 0.0f) {
            throw new IOException("SVG viewBox " + name + " must be positive");
        }
        double rounded = Math.ceil(value);
        if (rounded > MAX_DIMENSION) {
            throw new IOException("SVG " + name + " exceeds the dimension limit");
        }
        return Math.max(1, (int) rounded);
    }

    private static int[] rasterDimensions(String source, Size viewBox) throws IOException {
        float width = Float.NaN;
        float height = Float.NaN;
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
            if (root != null && "svg".equalsIgnoreCase(root.getTagName())) {
                width = intrinsicLength(root.getAttribute("width"), "width");
                height = intrinsicLength(root.getAttribute("height"), "height");
            }
        } catch (IOException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IOException("Invalid SVG intrinsic dimensions", exception);
        }

        // Percentages and relative CSS units depend on a host viewport. Keep the
        // stable viewBox raster in that case instead of guessing a device size.
        if (!Float.isFinite(width) && !Float.isFinite(height)) {
            return new int[]{
                    rasterDimension(viewBox.width(), "width"),
                    rasterDimension(viewBox.height(), "height")};
        }
        if (!Float.isFinite(width)) {
            width = height * viewBox.width() / viewBox.height();
        }
        if (!Float.isFinite(height)) {
            height = width * viewBox.height() / viewBox.width();
        }
        return new int[]{rasterDimension(width, "width"), rasterDimension(height, "height")};
    }

    private static float intrinsicLength(String value, String name) throws IOException {
        if (value == null || value.isBlank()) {
            return Float.NaN;
        }
        String normalized = value.trim().toLowerCase(java.util.Locale.ROOT);
        if (normalized.endsWith("%") || normalized.endsWith("em")
                || normalized.endsWith("rem") || normalized.endsWith("pt")
                || normalized.endsWith("pc") || normalized.endsWith("cm")
                || normalized.endsWith("mm") || normalized.endsWith("in")) {
            return Float.NaN;
        }
        if (normalized.endsWith("px")) {
            normalized = normalized.substring(0, normalized.length() - 2).trim();
        }
        try {
            float parsed = Float.parseFloat(normalized);
            if (!Float.isFinite(parsed) || parsed <= 0.0f) {
                throw new NumberFormatException(normalized);
            }
            return parsed;
        } catch (NumberFormatException exception) {
            throw new IOException("Invalid SVG " + name + " dimension", exception);
        }
    }

    private static void renderLayer(Graphics2D graphics, IconDefinition.Layer layer)
            throws IOException {
        Shape shape = toShape(layer.path());
        if (layer.fill() != null) {
            graphics.setColor(toAwtColor(layer.fill()));
            graphics.fill(shape);
        }
        if (layer.stroke() != null) {
            StrokeStyle style = layer.strokeStyle();
            graphics.setColor(toAwtColor(layer.stroke()));
            graphics.setStroke(new BasicStroke(
                    style.width(),
                    cap(style.cap()),
                    join(style.join()),
                    style.miterLimit()));
            graphics.draw(shape);
        }
    }

    private static java.awt.Color toAwtColor(Paint paint) throws IOException {
        if (!(paint instanceof SolidPaint solid)) {
            throw new IOException("SVG resource gradients are not supported by the CPU rasterizer");
        }
        Color color = solid.color();
        return new java.awt.Color(
                channel(color.red()),
                channel(color.green()),
                channel(color.blue()),
                channel(color.alpha()));
    }

    private static int channel(float value) {
        return Math.max(0, Math.min(255, Math.round(value * 255.0f)));
    }

    private static int cap(LineCap cap) {
        return switch (cap) {
            case BUTT -> BasicStroke.CAP_BUTT;
            case ROUND -> BasicStroke.CAP_ROUND;
            case SQUARE -> BasicStroke.CAP_SQUARE;
        };
    }

    private static int join(LineJoin join) {
        return switch (join) {
            case MITER -> BasicStroke.JOIN_MITER;
            case ROUND -> BasicStroke.JOIN_ROUND;
            case BEVEL -> BasicStroke.JOIN_BEVEL;
        };
    }

    private static Shape toShape(Path path) {
        PathAdapter adapter = new PathAdapter();
        path.replay(adapter);
        return adapter.path;
    }

    private static final class PathAdapter implements cn.fandmc.fandui.api.canvas.PathVisitor {
        private final Path2D.Float path = new Path2D.Float(Path2D.WIND_NON_ZERO);
        private float currentX;
        private float currentY;
        private float subpathX;
        private float subpathY;
        private boolean current;

        @Override
        public void moveTo(float x, float y) {
            path.moveTo(x, y);
            currentX = subpathX = x;
            currentY = subpathY = y;
            current = true;
        }

        @Override
        public void lineTo(float x, float y) {
            ensureCurrent(x, y);
            path.lineTo(x, y);
            currentX = x;
            currentY = y;
        }

        @Override
        public void quadTo(float controlX, float controlY, float x, float y) {
            ensureCurrent(x, y);
            path.quadTo(controlX, controlY, x, y);
            currentX = x;
            currentY = y;
        }

        @Override
        public void bezierTo(float control1X, float control1Y, float control2X,
                             float control2Y, float x, float y) {
            ensureCurrent(x, y);
            path.curveTo(control1X, control1Y, control2X, control2Y, x, y);
            currentX = x;
            currentY = y;
        }

        @Override
        public void arc(float centerX, float centerY, float radius,
                        float startRadians, float endRadians, ArcDirection direction) {
            double start = startRadians;
            double delta = directedDelta(start, endRadians, direction);
            double startX = centerX + radius * Math.cos(start);
            double startY = centerY + radius * Math.sin(start);
            if (!current) {
                moveTo((float) startX, (float) startY);
            } else if (Math.abs(currentX - startX) > 0.0001f
                    || Math.abs(currentY - startY) > 0.0001f) {
                lineTo((float) startX, (float) startY);
            }
            int segments = Math.max(1, (int) Math.ceil(Math.abs(delta) / (Math.PI * 0.5)));
            if (Math.abs(delta) <= ARC_EPSILON) {
                currentX = (float) startX;
                currentY = (float) startY;
                return;
            }
            double step = delta / segments;
            for (int index = 0; index < segments; index++) {
                double a0 = start + index * step;
                double a1 = a0 + step;
                double k = 4.0 / 3.0 * Math.tan((a1 - a0) / 4.0);
                double cos0 = Math.cos(a0);
                double sin0 = Math.sin(a0);
                double cos1 = Math.cos(a1);
                double sin1 = Math.sin(a1);
                float c1x = (float) (centerX + radius * (cos0 - k * sin0));
                float c1y = (float) (centerY + radius * (sin0 + k * cos0));
                float c2x = (float) (centerX + radius * (cos1 + k * sin1));
                float c2y = (float) (centerY + radius * (sin1 - k * cos1));
                float endX = (float) (centerX + radius * cos1);
                float endY = (float) (centerY + radius * sin1);
                path.curveTo(c1x, c1y, c2x, c2y, endX, endY);
                currentX = endX;
                currentY = endY;
            }
        }

        @Override
        public void rect(Rect rect) {
            path.append(new Rectangle2D.Float(rect.x(), rect.y(), rect.width(), rect.height()), false);
            currentX = rect.x();
            currentY = rect.y();
            subpathX = currentX;
            subpathY = currentY;
            current = true;
        }

        @Override
        public void roundedRect(Rect rect, CornerRadii radii) {
            Path2D.Float rounded = roundedRectangle(rect, radii);
            path.append(rounded, false);
            currentX = rect.x();
            currentY = rect.y();
            subpathX = currentX;
            subpathY = currentY;
            current = true;
        }

        @Override
        public void close() {
            path.closePath();
            currentX = subpathX;
            currentY = subpathY;
            current = true;
        }

        @Override
        public void winding(PathWinding winding) {
            path.setWindingRule(winding == PathWinding.HOLE
                    ? Path2D.WIND_EVEN_ODD : Path2D.WIND_NON_ZERO);
        }

        private void ensureCurrent(float x, float y) {
            if (!current) {
                moveTo(x, y);
            }
        }
    }

    private static double directedDelta(double start, double end, ArcDirection direction) {
        double delta = end - start;
        if (direction == ArcDirection.CLOCKWISE) {
            while (delta < 0.0) delta += TWO_PI;
            while (delta > TWO_PI) delta -= TWO_PI;
        } else {
            while (delta > 0.0) delta -= TWO_PI;
            while (delta < -TWO_PI) delta += TWO_PI;
        }
        return delta;
    }

    private static Path2D.Float roundedRectangle(Rect rect, CornerRadii radii) {
        float width = rect.width();
        float height = rect.height();
        float topLeft = radii.topLeft();
        float topRight = radii.topRight();
        float bottomRight = radii.bottomRight();
        float bottomLeft = radii.bottomLeft();
        float scale = Math.min(1.0f, Math.min(
                Math.min(width / Math.max(0.0001f, topLeft + topRight),
                        width / Math.max(0.0001f, bottomLeft + bottomRight)),
                Math.min(height / Math.max(0.0001f, topLeft + bottomLeft),
                        height / Math.max(0.0001f, topRight + bottomRight))));
        topLeft *= scale;
        topRight *= scale;
        bottomRight *= scale;
        bottomLeft *= scale;
        float x = rect.x();
        float y = rect.y();
        float right = x + width;
        float bottom = y + height;
        float k = 0.55228475f;
        Path2D.Float path = new Path2D.Float();
        path.moveTo(x + topLeft, y);
        path.lineTo(right - topRight, y);
        path.curveTo(right - topRight + k * topRight, y, right, y + topRight - k * topRight,
                right, y + topRight);
        path.lineTo(right, bottom - bottomRight);
        path.curveTo(right, bottom - bottomRight + k * bottomRight, right - bottomRight + k * bottomRight,
                bottom, right - bottomRight, bottom);
        path.lineTo(x + bottomLeft, bottom);
        path.curveTo(x + bottomLeft - k * bottomLeft, bottom, x, bottom - bottomLeft + k * bottomLeft,
                x, bottom - bottomLeft);
        path.lineTo(x, y + topLeft);
        path.curveTo(x, y + topLeft - k * topLeft, x + topLeft - k * topLeft, y,
                x + topLeft, y);
        path.closePath();
        return path;
    }
}
