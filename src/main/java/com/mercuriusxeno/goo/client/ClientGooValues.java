package com.mercuriusxeno.goo.client;

import com.mercuriusxeno.goo.data.GooValueSource;
import com.mercuriusxeno.goo.data.GooValueTable;
import com.mercuriusxeno.goo.data.IGooValueLookup;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * The goo values the client's connection holds, for client code that reads
 * values with no level at hand (decision type-package-and-per-server-holders).
 */
public final class ClientGooValues {

    private static final AtomicReference<Supplier<IGooValueLookup>> SOURCE =
            new AtomicReference<>(ClientGooValues::connectionValues);

    private ClientGooValues() {
    }

    /**
     * Answers the values the current connection received.
     *
     * @return the values, empty while no connection stands or no client runs
     */
    public static IGooValueLookup current() {
        return SOURCE.get().get();
    }

    /**
     * Points {@link #current()} at another source, as a test hands the wheel a fake.
     *
     * @param source the values to answer
     * @return the source that stood before, to restore
     */
    public static Supplier<IGooValueLookup> readFrom(Supplier<IGooValueLookup> source) {
        return SOURCE.getAndSet(source);
    }

    private static IGooValueLookup connectionValues() {
        // No client runs in a unit test or on a dedicated server, so there is no connection to read.
        Minecraft minecraft = Minecraft.getInstance();
        ClientPacketListener connection = minecraft == null ? null : minecraft.getConnection();
        if (connection instanceof GooValueSource source) {
            return source.gooValueLookup();
        }
        return GooValueTable.EMPTY;
    }
}
