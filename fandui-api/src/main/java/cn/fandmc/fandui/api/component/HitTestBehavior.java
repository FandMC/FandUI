package cn.fandmc.fandui.api.component;

/** Controls whether a component participates in pointer hit testing. */
public enum HitTestBehavior {
    /** The component can become the pointer target. */
    OPAQUE,
    /** The component is skipped while its descendants remain eligible. */
    PASS_THROUGH,
    /** The component and its complete subtree are skipped. */
    IGNORE_SUBTREE
}
