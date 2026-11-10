package fr.neamar.kiss.ui;

/** Geometry shared by the workspace and JVM regression tests. */
public final class WidgetGridRules {
    private WidgetGridRules() { }
    public static boolean valid(int x, int y, int width, int height) {
        return x >= 0 && y >= 0 && y <= 10000 && width > 0 && width <= 4 && x + width <= 4 && height > 0 && height <= 200;
    }
    public static boolean overlaps(int x, int y, int w, int h, int ox, int oy, int ow, int oh) {
        return x < ox + ow && x + w > ox && y < oy + oh && y + h > oy;
    }
}
