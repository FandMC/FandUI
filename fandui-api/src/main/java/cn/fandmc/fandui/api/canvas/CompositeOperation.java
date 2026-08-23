package cn.fandmc.fandui.api.canvas;

/** Porter-Duff composition mode applied to subsequent premultiplied-alpha drawing. */
public enum CompositeOperation {
    SOURCE_OVER,
    SOURCE_IN,
    SOURCE_OUT,
    SOURCE_ATOP,
    DESTINATION_OVER,
    DESTINATION_IN,
    DESTINATION_OUT,
    DESTINATION_ATOP,
    LIGHTER,
    COPY,
    XOR
}
