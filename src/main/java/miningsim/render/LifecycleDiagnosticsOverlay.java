package miningsim.render;

import java.awt.Graphics2D;
import java.util.Locale;
import miningsim.app.InteractiveSession;

/** Draws lifecycle diagnostics from the current session without mutating simulation state. */
public final class LifecycleDiagnosticsOverlay {
    private LifecycleDiagnosticsOverlay() { }
    public static void render(Graphics2D g,InteractiveSession session) {
        if(session.lifecycle()==null) return;
        var d=session.lifecycle().diagnostics();
        g.drawString(String.format(Locale.ROOT,"Rigid bodies: %d | Sleeping: %d | Settled: %d | Pending support: %d",
                d.rigidBodies(),session.physics().interactionDiagnostics().sleepingBodies(),d.settledRocks(),d.pendingSupport()),8,308);
        g.drawString(String.format(Locale.ROOT,"Settling: %d | Reactivated: %d | Lifecycle rejects: %d",
                d.settled(),d.reactivated(),d.rejected()),8,325);
        String reason=d.lastReject(); if(reason.length()>95) reason=reason.substring(0,92)+"...";
        g.drawString("Last lifecycle reject: "+reason,8,342);
        g.drawString(String.format(Locale.ROOT,"Settling ms: %.3f | Support ms: %.3f",
                (d.planningNanos()+d.commitNanos())/1e6,d.supportNanos()/1e6),8,359);
    }
}
