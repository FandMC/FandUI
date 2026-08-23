package cn.fandmc.fandui.api.animation;

/** Maps normalized linear time to animation progress. */
@FunctionalInterface
public interface Easing {
    double transform(double progress);
}
