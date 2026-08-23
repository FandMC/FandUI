/**
 * Stable, platform-neutral entry points for FandUI.
 *
 * <p>Unless a method explicitly states otherwise, live runtime, session, component,
 * controller, focus, animation, Screen, HUD, and registration operations belong to
 * the FandUI UI thread. Use {@link cn.fandmc.fandui.api.UiRuntime#execute(Runnable)}
 * to enter that thread. Immutable value objects may be read from any thread.</p>
 */
@org.jspecify.annotations.NullMarked
package cn.fandmc.fandui.api;
