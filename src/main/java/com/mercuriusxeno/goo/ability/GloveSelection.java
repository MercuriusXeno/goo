package com.mercuriusxeno.goo.ability;

import com.mercuriusxeno.goo.GooTypeDefinition;
import com.mercuriusxeno.goo.GooTypes;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import org.jspecify.annotations.Nullable;

/**
 * The player's goo type and ability selection on a glove. A glove holds
 * both or neither: every selection names an ability (decision
 * no-throw-without-ability).
 *
 * @param gooTypeId the selected goo type's string id (empty = none)
 * @param abilityId the selected ability's resource id string (empty = none)
 */
public record GloveSelection(String gooTypeId, String abilityId) {

    /** Empty string sentinel for unset fields. */
    private static final String NONE = "";

    /** Empty selection - no type, no ability. */
    public static final GloveSelection EMPTY = new GloveSelection(NONE, NONE);

    /** Persistent codec for data component storage. */
    public static final Codec<GloveSelection> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.STRING.optionalFieldOf("gooTypeId", "").forGetter(GloveSelection::gooTypeId),
            Codec.STRING.optionalFieldOf("abilityId", "").forGetter(GloveSelection::abilityId)
    ).apply(inst, GloveSelection::new));

    /** Network codec for client-server sync. */
    public static final StreamCodec<ByteBuf, GloveSelection> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8, GloveSelection::gooTypeId,
                    ByteBufCodecs.STRING_UTF8, GloveSelection::abilityId,
                    GloveSelection::new);

    /**
     * Creates a full selection with type and ability.
     *
     * @param type      the goo type
     * @param abilityId the ability resource identifier
     * @return a selection with both type and ability
     */
    public static GloveSelection ofAbility(ResourceKey<GooTypeDefinition> type, Identifier abilityId) {
        return new GloveSelection(GooTypes.id(type), abilityId.toString());
    }

    /**
     * Resolves the goo type from the stored id.
     *
     * @return the goo type key, or null if empty or unknown
     */
    public @Nullable ResourceKey<GooTypeDefinition> getGooType() {
        if (gooTypeId.isEmpty()) { return null; }
        return GooTypes.byId(gooTypeId);
    }

    /**
     * Resolves the ability identifier from the stored string.
     *
     * @return the Identifier, or null if empty
     */
    public @Nullable Identifier getAbilityIdentifier() {
        if (abilityId.isEmpty()) { return null; }
        return Identifier.tryParse(abilityId);
    }

    /**
     * Returns true if a goo type is selected.
     *
     * @return true if type is set
     */
    public boolean hasType() {
        return !gooTypeId.isEmpty();
    }

    /**
     * Returns true if a specific ability is selected.
     *
     * @return true if ability is set
     */
    public boolean hasAbility() {
        return !abilityId.isEmpty();
    }

    /**
     * Returns true if nothing is selected.
     *
     * @return true if both type and ability are empty
     */
    public boolean isEmpty() {
        return gooTypeId.isEmpty();
    }
}
