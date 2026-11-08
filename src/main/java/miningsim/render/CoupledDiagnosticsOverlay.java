package miningsim.render;

import java.awt.Graphics2D;
import java.util.Locale;
import miningsim.app.InteractiveSession;

/** Draws the previously published coupled diagnostics; never advances the simulation. */
public final class CoupledDiagnosticsOverlay {
    private CoupledDiagnosticsOverlay() { }
    public static void render(Graphics2D g,InteractiveSession session) {
        if(!session.isCoupled()) { g.drawString("LEGACY",8,135); return; }
        var d=session.coupled().diagnostics(); var o=session.occupancy().snapshot().diagnostics();
        g.drawString(String.format(Locale.ROOT,"COUPLED | bodies %d sleeping %d | occupancy %d (%d cells) | tick %d",
                o.indexedBodies(),session.physics().interactionDiagnostics().sleepingBodies(),o.revision(),o.occupiedCells(),d.ticks()),8,135);
        g.drawString(String.format(Locale.ROOT,"Displaced S/G/S/W %d/%d/%d/%d | cells %s | microsteps %d",
                d.sand(),d.gravel(),d.soil(),d.water(),d.cellularOutcome(),session.physics().motionDiagnostics().executedMicrosteps()),8,152);
        String message=session.failure()!=null?session.failure():session.structural()!=null?session.structural().extractionMessage():"";
        if(message.length()>100) message=message.substring(0,97)+"...";
        g.drawString((session.blocked()?"PAUSED: ":"")+message,8,169);
    }
}
