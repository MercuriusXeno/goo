package com.mercuriusxeno.goo.ability.zone;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.phys.Vec3;
import java.util.Optional;

/**
 * The shifter a player holds: each hit they would take blinks them the
 * distance along their look instead, and a fall out of the world returns
 * them to the last solid ground they stood on, until the game time it fades
 * at. Saved with the player.
 * Decision shifter-blinks-along-the-cursor-on-hit.
 *
 * @param distance   how far each hit blinks the player along their look
 * @param expiresAt  the game time it fades at; zero when none stands
 * @param safeGround the last solid ground the player stood on while it stood, empty before any
 */
public record Shifter(float distance, long expiresAt, Optional<Vec3> safeGround) {

    /** No shifter. */
    public static final Shifter NONE = new Shifter(0f, 0L, Optional.empty());
    /** The fade time of shifter held until its held effect ends. */
    public static final long NEVER_EXPIRES = Long.MAX_VALUE;

    private static final String FIELD_DISTANCE = "distance";
    private static final String FIELD_EXPIRES_AT = "expires_at";
    private static final String FIELD_SAFE_GROUND = "safe_ground";

    /** Saves the shifter with the player. */
    public static final MapCodec<Shifter> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Codec.FLOAT.fieldOf(FIELD_DISTANCE).forGetter(Shifter::distance),
            Codec.LONG.fieldOf(FIELD_EXPIRES_AT).forGetter(Shifter::expiresAt),
            Vec3.CODEC.optionalFieldOf(FIELD_SAFE_GROUND).forGetter(Shifter::safeGround)
    ).apply(inst, Shifter::new));

    /**
     * Whether shifter stands at a game time.
     *
     * @param gameTime the game time
     * @return true before it fades
     */
    public boolean standsAt(long gameTime) {
        return gameTime < expiresAt;
    }

    /**
     * A cast from the glove: shifter stands until its held effect ends,
     * which clears it (decision self-effects-trickle-until-ended).
     *
     * @param castDistance the cast's blink distance
     * @return the shifter after the cast
     */
    public Shifter hold(float castDistance) {
        return new Shifter(castDistance, NEVER_EXPIRES, safeGround);
    }

    /**
     * A drunk brew: shifter stands for the brew's duration, or as long
     * as it already stood where that is longer.
     *
     * @param brewDistance the brew's blink distance
     * @param ticks        the brew's duration
     * @param gameTime     the game time of the drink
     * @return the shifter after the drink
     */
    public Shifter brew(float brewDistance, int ticks, long gameTime) {
        return new Shifter(brewDistance, Math.max(expiresAt, gameTime + ticks), safeGround);
    }

    /**
     * Shifter remembering where the player last stood on solid ground.
     *
     * @param ground the player's feet on that ground
     * @return the shifter with the ground remembered
     */
    public Shifter standingOn(Vec3 ground) {
        return new Shifter(distance, expiresAt, Optional.of(ground));
    }
}
