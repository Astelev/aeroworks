package net.aste.aeroworks.failure;

/** Owned by a block entity. Only the failed flag is read by the physics thread. */
public final class FailureState {
    private volatile boolean failed;
    private int graceTicks;
    public boolean failed() { return failed; }
    public int graceTicks() { return graceTicks; }
    public void restore(boolean failed,int graceTicks) {
        this.graceTicks=Math.max(0,Math.min(72000,graceTicks));
        this.failed=failed;
    }
    public boolean canFail() { return !failed && graceTicks==0; }
    public boolean tryFail(double probability,double roll) {
        if(!canFail()||!Double.isFinite(probability)||probability<0||probability>1
                ||!Double.isFinite(roll)||roll<0||roll>=1||roll>=probability)return false;
        failed=true;return true;
    }
    public boolean repair(int graceTicks) {
        if(!failed)return false;
        this.graceTicks=Math.max(0,Math.min(72000,graceTicks));
        failed=false;return true;
    }
    public void elapse(int ticks) { graceTicks=Math.max(0,graceTicks-Math.max(0,ticks)); }
}
