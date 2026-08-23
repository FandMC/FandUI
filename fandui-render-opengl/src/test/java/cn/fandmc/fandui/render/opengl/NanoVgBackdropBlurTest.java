package cn.fandmc.fandui.render.opengl;

import cn.fandmc.fandui.api.layout.Rect;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NanoVgBackdropBlurTest {
    @Test
    void reusesPlansUntilAPlanningInputChanges() {
        NanoVgBackdropBlur blur = new NanoVgBackdropBlur();
        NanoVgBackdropBlur.Plan first = blur.planFor(1920, 1121, 2.0f, 18.0f);

        assertSame(first, blur.planFor(1920, 1121, 2.0f, 18.0f));
        assertNotSame(first, blur.planFor(1920, 1121, 2.0f, 19.0f));
    }

    @Test
    void budgetsTwoReusableHalfResolutionRgbaTargets() {
        assertEquals(960L * 540L * 8L, NanoVgBackdropBlur.requiredBytes(1920, 1080));
        assertEquals(1L * 1L * 8L, NanoVgBackdropBlur.requiredBytes(1, 1));
        assertThrows(IllegalArgumentException.class, () -> NanoVgBackdropBlur.requiredBytes(0, 1));
        assertThrows(OpenGlRenderException.class,
                () -> NanoVgBackdropBlur.requiredBytes(Integer.MAX_VALUE, Integer.MAX_VALUE));
    }

    @Test
    void quantizesPhysicalRadiusAndSelectsAStableDownsample() {
        NanoVgBackdropBlur.Plan plan = NanoVgBackdropBlur.plan(1920, 1080, 2.0f, 18.0f);

        assertEquals(4, plan.downsample());
        assertEquals(2, plan.downsampleStages());
        assertEquals(480, plan.width());
        assertEquals(270, plan.height());
        assertEquals(36.0f, plan.physicalRadius());
        assertEquals(1.0f, plan.kernel().normalizedWeight(), 1.0e-5f);
    }

    @Test
    void keepsSmallBlurAtHalfResolutionAndRejectsInvalidPlans() {
        NanoVgBackdropBlur.Plan plan = NanoVgBackdropBlur.plan(101, 55, 1.0f, 2.0f);

        assertEquals(2, plan.downsample());
        assertEquals(1, plan.downsampleStages());
        assertEquals(51, plan.width());
        assertEquals(28, plan.height());
        assertThrows(IllegalArgumentException.class,
                () -> NanoVgBackdropBlur.plan(100, 100, 1.0f, 0.0f));
        assertThrows(IllegalArgumentException.class,
                () -> NanoVgBackdropBlur.plan(100, 100, Float.NaN, 2.0f));
    }

    @Test
    void restrictsOddSizedPrefilterStagesToTheBlurSupportRegion() {
        NanoVgBackdropBlur.Plan plan = NanoVgBackdropBlur.plan(1920, 1121, 2.0f, 18.0f);
        NanoVgBackdropBlur.PixelBounds affected = new NanoVgBackdropBlur.PixelBounds(
                959,
                0,
                961,
                1121);

        NanoVgBackdropBlur.PixelBounds half = NanoVgBackdropBlur.prefilterBounds(
                affected,
                2,
                960,
                561,
                plan);
        NanoVgBackdropBlur.PixelBounds quarter = NanoVgBackdropBlur.prefilterBounds(
                affected,
                4,
                480,
                281,
                plan);

        assertEquals(new NanoVgBackdropBlur.PixelBounds(469, 0, 491, 561), half);
        assertEquals(new NanoVgBackdropBlur.PixelBounds(235, 0, 245, 281), quarter);
        long fullStageArea = 960L * 561L + 480L * 281L;
        long restrictedArea = half.area() + quarter.area();
        assertEquals(344_296L, restrictedArea);
        assertEquals(0.5112497f, (float) restrictedArea / fullStageArea, 1.0e-6f);
    }

    @Test
    void buildsAnExactFractionalAxisAlignedCompositeMask() {
        NanoVgBackdropBlur.CompositeMask mask = NanoVgBackdropBlur.axisAlignedMask(
                new Rect(0.0f, 0.0f, 160.5f, 101.0f),
                new float[] {1.0f, 0.0f, 0.0f, 1.0f, 160.5f, 0.0f},
                2.0f,
                642,
                202,
                0.75f);

        assertEquals(true, mask.analytic());
        assertEquals(new NanoVgBackdropBlur.PixelBounds(321, 0, 321, 202), mask.bounds());
        assertEquals(321.0f, mask.left());
        assertEquals(642.0f, mask.right());
        assertEquals(0.0f, mask.bottom());
        assertEquals(202.0f, mask.top());
        assertEquals(0.75f, mask.alpha());
    }

    @Test
    void leavesRotatedMasksOnTheNanoVgFallback() {
        assertEquals(null, NanoVgBackdropBlur.axisAlignedMask(
                new Rect(0.0f, 0.0f, 50.0f, 50.0f),
                new float[] {0.9f, 0.1f, -0.1f, 0.9f, 10.0f, 20.0f},
                1.0f,
                200,
                200,
                1.0f));
    }

    @Test
    void convertsTransformedLogicalBoundsToConservativeOpenGlScissor() {
        NanoVgBackdropBlur.PixelBounds bounds = NanoVgBackdropBlur.transformedBounds(
                new Rect(10.0f, 20.0f, 30.0f, 40.0f),
                new float[] {1.0f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f},
                2.0f,
                200,
                200);

        assertEquals(new NanoVgBackdropBlur.PixelBounds(19, 79, 62, 82), bounds);
        assertEquals(
                new NanoVgBackdropBlur.PixelBounds(0, 0, 0, 0),
                NanoVgBackdropBlur.transformedBounds(
                        new Rect(10.0f, 20.0f, 30.0f, 40.0f),
                        new float[] {1.0f, 0.0f, 0.0f, 1.0f, 500.0f, 500.0f},
                        1.0f,
                        200,
                        200));
    }
}
