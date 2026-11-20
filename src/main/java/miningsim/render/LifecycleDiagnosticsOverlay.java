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
        var assembly=session.lifecycle().assemblyDiagnostics();
        g.drawString(String.format(Locale.ROOT,"Assemblies: %d | Supported settled: %d | Support contacts: %d | %s",
                assembly.assemblies(),assembly.supported(),assembly.contacts(),assembly.complete()?"current":"analysis pending"),8,376);
        g.drawString(String.format(Locale.ROOT,"Support dirty: %d | Assembly analysis ms: %.3f",
                d.pendingSupport(),session.lifecycle().assemblyAnalysisNanos()/1e6),8,393);
        String settling=session.lifecycle().lastSettlingReject(),support=session.lifecycle().lastSupportReject();
        if(settling.length()>90) settling=settling.substring(0,87)+"...";
        if(support.length()>90) support=support.substring(0,87)+"...";
        g.drawString("Last settling reject: "+settling,8,410);
        g.drawString("Last support reject: "+support,8,427);
    }
}
