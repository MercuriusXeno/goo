package com.mercuriusxeno.goo.ability;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;
import org.jspecify.annotations.NonNull;

/**
 * When an ability's indicator shows, read from its JSON's {@code indicator}
 * field: while right click is held, or whenever the ability is the one
 * selected. Blink's cursor shows while held and may switch to selected.
 * Decision ripple-outline-is-the-blink-cursor.
 */
public enum IndicatorShowing implements StringRepresentable {
    /** Only while right click holds a live press. */
    HELD("held"),
    /** Whenever the ability is the glove's selected one. */
    SELECTED("selected");

    /** Datapack codec, reading the rule by its word. */
    public static final Codec<IndicatorShowing> CODEC = StringRepresentable.fromEnum(IndicatorShowing::values);

    /** Network codec, writing the rule as a varint ordinal. */
    public static final StreamCodec<ByteBuf, IndicatorShowing> STREAM_CODEC =
            ByteBufCodecs.VAR_INT.map(ordinal -> values()[ordinal], IndicatorShowing::ordinal);

    private final String serializedName;

    IndicatorShowing(String serializedName) {
        this.serializedName = serializedName;
    }

    /**
     * Whether the indicator shows this frame.
     *
     * @param useHeld whether right click holds a live press
     * @return true while held, or always for an indicator shown whenever selected
     */
    public boolean shows(boolean useHeld) {
        return this == SELECTED || useHeld;
    }

    @Override
    public @NonNull String getSerializedName() {
        return serializedName;
    }
}
