package fr.neamar.kiss.ui;

/** Single owner for one pointer stream; no taps or long presses are synthesized. */
public final class WidgetSwipe {
    private float x, y, threshold;
    private long start, timeout;
    private boolean eligible, claimed;
    private String direction;
    public void start(float x, float y, long time, float threshold, long timeout, boolean eligible) {
        this.x=x; this.y=y; start=time; this.threshold=threshold; this.timeout=timeout;
        this.eligible=eligible; claimed=false; direction=null;
    }
    public boolean move(float x, float y, long time) {
        if (claimed) return true;
        if (!eligible) return false;
        if (time-start >= timeout) { cancel(); return false; }
        String candidate=direction(x-this.x,y-this.y,threshold);
        if (candidate != null) { claimed=true; direction=candidate; }
        return claimed;
    }
    public String finish(float x, float y, long time) {
        String result=claimed && time-start < 1000 && direction.equals(direction(x-this.x,y-this.y,threshold)) ? direction : null;
        cancel(); return result;
    }
    public boolean isClaimed() { return claimed; }
    public void cancel() { eligible=false; claimed=false; direction=null; }
    public static String direction(float dx, float dy, float threshold) {
        float ax=Math.abs(dx), ay=Math.abs(dy);
        if (ax >= threshold && ax > ay*1.5f) return dx>0 ? "right" : "left";
        if (ay >= threshold && ay > ax*1.5f) return dy>0 ? "down" : "up";
        return null;
    }
}
