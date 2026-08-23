package cn.fandmc.fandui.render.opengl;

public record OpenGlRenderReport(
        Status status,
        String hostName,
        int framebuffer,
        int width,
        int height,
        int batches,
        int drawCalls
) {
    public enum Status {
        NO_TARGET,
        TARGET_REBUILT,
        RENDERED
    }
}
