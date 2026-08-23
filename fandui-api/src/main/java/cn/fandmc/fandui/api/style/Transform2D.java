package cn.fandmc.fandui.api.style;

import cn.fandmc.fandui.internal.validation.Preconditions;
import cn.fandmc.fandui.api.layout.Point;

import java.util.Objects;
import java.util.Optional;

/**
 * Immutable affine transform mapping local logical coordinates to parent coordinates.
 * Composition, scene geometry, hit testing, clipping, and focus traversal share this matrix.
 */
public record Transform2D(float m00, float m01, float m10, float m11, float tx, float ty) {
    private static final Transform2D IDENTITY = new Transform2D(1.0f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f);

    public Transform2D {
        Preconditions.finite(m00, "m00");
        Preconditions.finite(m01, "m01");
        Preconditions.finite(m10, "m10");
        Preconditions.finite(m11, "m11");
        Preconditions.finite(tx, "tx");
        Preconditions.finite(ty, "ty");
    }

    public static Transform2D identity() {
        return IDENTITY;
    }

    public static Transform2D translation(float x, float y) {
        return new Transform2D(1.0f, 0.0f, 0.0f, 1.0f, x, y);
    }

    public static Transform2D scale(float x, float y) {
        return new Transform2D(x, 0.0f, 0.0f, y, 0.0f, 0.0f);
    }

    public static Transform2D rotation(float radians) {
        Preconditions.finite(radians, "radians");
        float cosine = (float) Math.cos(radians);
        float sine = (float) Math.sin(radians);
        return new Transform2D(cosine, sine, -sine, cosine, 0.0f, 0.0f);
    }

    /**
     * Concatenates {@code next} after this transform in canvas call order.
     * A point is first mapped by {@code next}, then by this transform.
     */
    public Transform2D concatenate(Transform2D next) {
        Objects.requireNonNull(next, "next");
        return new Transform2D(
                m00 * next.m00 + m10 * next.m01,
                m01 * next.m00 + m11 * next.m01,
                m00 * next.m10 + m10 * next.m11,
                m01 * next.m10 + m11 * next.m11,
                m00 * next.tx + m10 * next.ty + tx,
                m01 * next.tx + m11 * next.ty + ty);
    }

    public Point map(Point point) {
        Objects.requireNonNull(point, "point");
        return new Point(
                m00 * point.x() + m10 * point.y() + tx,
                m01 * point.x() + m11 * point.y() + ty);
    }

    public Optional<Transform2D> inverse() {
        float determinant = m00 * m11 - m10 * m01;
        if (!Float.isFinite(determinant) || Math.abs(determinant) <= 1.0e-7f) {
            return Optional.empty();
        }
        float inverse = 1.0f / determinant;
        float i00 = m11 * inverse;
        float i01 = -m01 * inverse;
        float i10 = -m10 * inverse;
        float i11 = m00 * inverse;
        return Optional.of(new Transform2D(
                i00,
                i01,
                i10,
                i11,
                -(i00 * tx + i10 * ty),
                -(i01 * tx + i11 * ty)));
    }
}
