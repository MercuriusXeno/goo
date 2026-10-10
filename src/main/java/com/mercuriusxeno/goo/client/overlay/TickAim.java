package com.mercuriusxeno.goo.client.overlay;

import com.mercuriusxeno.goo.network.TickAimPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jspecify.annotations.Nullable;

/**
 * The block the server last said a held Tick stream hastens, so the client
 * highlights a block only where the server ticks it: the client cannot answer
 * that alone, since a block's client ticker differs from its server ticker
 * (a chest animates its lid on the client and ticks nothing on the server).
 * tick-channel-marches-squares-on-the-face
 */
public final class TickAim {

    /** The client's one tick aim. */
    public static final TickAim CLIENT = new TickAim();

    /** Ticks a named block stays drawable without a fresh word from the server, riding out a late packet. */
    static final int FRESH_TICKS = 5;

    private @Nullable BlockPos ticked;
    private long namedAt;

    /**
     * Records the block the server named, or none.
     *
     * @param pos the block the stream hastens, null where it hastens none
     * @param now the client's game time
     */
    void name(@Nullable BlockPos pos, long now) {
        ticked = pos;
        namedAt = now;
    }

    /**
     * Whether the overlay draws on a block: the server named it within the
     * last few ticks.
     *
     * @param pos the block the client's look ends on
     * @param now the client's game time
     * @return true where the server hastens that block
     */
    boolean drawsOn(BlockPos pos, long now) {
        return pos.equals(ticked) && now - namedAt <= FRESH_TICKS;
    }

    /**
     * Handles the server's tick aim on the client thread.
     *
     * @param payload the tick aim
     * @param context the network context
     */
    public static void handle(TickAimPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level != null) {
                CLIENT.name(payload.ticked().orElse(null), mc.level.getGameTime());
            }
        });
    }
}
