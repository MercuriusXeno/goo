package com.mercuriusxeno.goo.client.entity;

import com.mercuriusxeno.goo.client.TranslucentVisual.LevelFrame;
import com.mercuriusxeno.goo.client.TranslucentVisual.SortedDraw;
import com.mercuriusxeno.goo.client.ability.PortalView;
import com.mercuriusxeno.goo.client.ability.WispGlow;
import com.mercuriusxeno.goo.client.ber.WispRenderer;
import com.mercuriusxeno.goo.entity.DriftingWisp;
import com.mercuriusxeno.goo.entity.DriftingWisp.Beam;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * The trains of fading copies beaming wisps leave on this client. As the
 * mote beams, it passes ECHOES evenly spaced places on its straight line,
 * and a copy of the mote stands at each place it passed, fainter than the
 * mote and fading out over ECHO_LIFE_TICKS from the moment the mote passed
 * it, so the oldest copy, nearest the goo, fades first and the train follows
 * the wisp to its spot and then fades away. A train lives on the client from
 * the beam the server synced, so it outlasts the drifting wisp, which leaves
 * a moment after it settles; no copy stands on the spot itself, where the
 * wisp block's mote draws. Each copy is queued to {@link WispGlow}, which
 * hands the shared path its draws.
 * beaming-wisp-leaves-fading-echoes-behind-it
 * translucent-goo-visuals-draw-through-one-checked-sorting-path
 */
public final class WispEchoes {

    /** The trains playing on this client. */
    public static final WispEchoes CLIENT = new WispEchoes();

    /** Copies along one beam, the first on the goo and the last short of the spot. */
    static final int ECHOES = 20;
    /** A fresh copy's strength, the mote's being one: smaller and fainter than the mote. */
    static final float FRESH_STRENGTH = 0.5f;
    /** Ticks a copy takes to fade out after the mote passed it, longer than the beam so the whole line shows. */
    static final float ECHO_LIFE_TICKS = 12f;
    /**
     * Ticks the drawn mote stands behind the game time: a frame draws an
     * entity between its last two positions, so the copies keep a tick
     * behind with it rather than running ahead of the mote.
     */
    private static final float DRAWN_MOTE_LAG_TICKS = 1f;
    /** A copy stands still: the mote's turn is too slight across one beam to show. */
    private static final float UNTURNED = 0f;

    private final Set<Train> live = new LinkedHashSet<>();

    private WispEchoes() {
    }

    /**
     * One copy of the mote as a frame draws it.
     *
     * @param position the world point it stands at
     * @param strength how much of the mote it shows, zero to one
     */
    public record Echo(Vec3 position, float strength) {
    }

    /**
     * One train playing: the beam it trails and the wisp that beamed it.
     *
     * @param wispId the drifting wisp's entity id
     * @param beam   the beam's line and when it set off
     */
    private record Train(int wispId, Beam beam) {

        float ticksSinceSetOff(double gameTime) {
            return (float) (gameTime - beam.setOffAt());
        }
    }

    /**
     * The copies of a beaming mote standing on its line a number of ticks
     * after it set off: one at each place the mote has passed, goo end
     * first, each fading out over the copy's life from the tick the mote
     * passed it, a faded copy reading zero.
     * beaming-wisp-leaves-fading-echoes-behind-it
     *
     * @param from             where the mote set off
     * @param to               the spot it heads for
     * @param ticksSinceSetOff ticks since it set off, with the partial tick
     * @return the copies the mote has passed, goo end first
     */
    static List<Echo> train(Vec3 from, Vec3 to, float ticksSinceSetOff) {
        List<Echo> echoes = new ArrayList<>();
        for (int echo = 0; echo < ECHOES && passedAt(echo) < ticksSinceSetOff; echo++) {
            float left = 1f - (ticksSinceSetOff - passedAt(echo)) / ECHO_LIFE_TICKS;
            echoes.add(new Echo(from.lerp(to, (double) echo / ECHOES), FRESH_STRENGTH * Math.clamp(left, 0f, 1f)));
        }
        return echoes;
    }

    /**
     * @param echo the copy's place along the line, 0 on the goo
     * @return the ticks after setting off the mote passes the copy's place
     */
    private static float passedAt(int echo) {
        return (float) echo * DriftingWisp.ARRIVAL_TICKS / ECHOES;
    }

    /**
     * @return the ticks after setting off the last copy of a train is gone
     */
    static float lastTick() {
        return passedAt(ECHOES - 1) + ECHO_LIFE_TICKS;
    }

    /**
     * Starts the train of a beam a drifting wisp holds, once for each beam:
     * a wisp beaming on to a second spot starts a second train, and its
     * first keeps fading.
     *
     * @param wispId the drifting wisp's entity id
     * @param beam   the beam the server synced
     */
    public void trail(int wispId, Beam beam) {
        live.add(new Train(wispId, beam));
    }

    /**
     * Drops every train that has faded and answers the copies still showing.
     *
     * @param gameTime the game time the frame draws, with the partial tick
     * @return the copies with a strength above nothing
     */
    List<Echo> showing(double gameTime) {
        live.removeIf(train -> train.ticksSinceSetOff(gameTime) > lastTick());
        List<Echo> showing = new ArrayList<>();
        for (Train train : live) {
            Beam beam = train.beam();
            for (Echo echo : train(beam.from(), beam.to(), train.ticksSinceSetOff(gameTime))) {
                if (echo.strength() > 0) {
                    showing.add(echo);
                }
            }
        }
        return showing;
    }

    /** Drops every train, as the client leaves a level. */
    public void clear() {
        live.clear();
    }

    /**
     * Queues each copy showing this frame to {@link WispGlow}, registered
     * ahead of it on the shared path, so it draws them this frame.
     * translucent-goo-visuals-draw-through-one-checked-sorting-path
     *
     * @param frame the frame being drawn
     * @param draws where draws go; the copies reach the path through WispGlow's
     */
    public static void collect(LevelFrame frame, Consumer<SortedDraw> draws) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        // a portal view's pass draws another level, which holds none of these trains
        if (level == null || PortalView.drawing()) {
            return;
        }
        double gameTime = level.getGameTime() + mc.getDeltaTracker().getGameTimeDeltaPartialTick(false)
                - DRAWN_MOTE_LAG_TICKS;
        for (Echo echo : CLIENT.showing(gameTime)) {
            WispGlow.queue(new WispGlow.Sprite(echo.position(), UNTURNED,
                    (pose, consumer) -> WispRenderer.drawMote(pose, consumer, echo.strength())));
        }
    }
}
