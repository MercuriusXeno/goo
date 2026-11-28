package com.mercuriusxeno.goo.ability.world;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.program.HostKind;
import com.mercuriusxeno.goo.ability.program.LandingHost;
import com.mercuriusxeno.goo.ability.program.LeafSteps;
import com.mercuriusxeno.goo.ability.program.MeteorStep;
import com.mercuriusxeno.goo.ability.program.ProgramBehavior;
import com.mercuriusxeno.goo.ability.program.ProgramLoadException;
import com.mercuriusxeno.goo.network.ChainBurnoutPayload;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import java.util.Optional;
import java.util.UUID;

/**
 * What an ability goo does when it lands on a block: it lands in the cell
 * {@link LandingSpot} decides, announces its burnout, and runs its program
 * on that landing the tick it splats. The landing places no block of its
 * own; its program leaves its effect, and an ability that stays in the world
 * places its own thing (decisions splat-runs-the-program-no-fuse,
 * lingering-abilities-place-their-own-thing).
 */
public final class AbilityImpact {

    private static final String LOG_PROGRAM_REFUSED = "Ability {} program refused for the landing host: {}";

    private AbilityImpact() {
    }

    /**
     * Lands an ability goo on a block.
     *
     * @param level   the server level
     * @param pos     the struck block
     * @param type    the goo type thrown
     * @param face    the struck face
     * @param ability the ability the goo names
     */
    public static void land(ServerLevel level, BlockPos pos, ResourceKey<GooTypeDefinition> type,
                            Direction face, AbilityDefinition ability) {
        land(level, pos, type, face, ability, null);
    }

    /**
     * Lands an ability goo on a block, its world actions anchored at the
     * aimed point where it names one, and at the landing cell's center
     * otherwise, so a free ability resolves where it was aimed.
     * aim-point-follows-the-cursor
     *
     * @param level   the server level
     * @param pos     the struck block
     * @param type    the goo type thrown
     * @param face    the struck face
     * @param ability the ability the goo names
     * @param point   the aimed point the ability resolves at, or null for the cell's center
     */
    public static void land(ServerLevel level, BlockPos pos, ResourceKey<GooTypeDefinition> type,
                            Direction face, AbilityDefinition ability, @Nullable Vec3 point) {
        land(level, pos, type, face, ability, point, 0);
    }

    /**
     * Lands an ability on a block at the size its cast was dragged to, which
     * its program reads; a throw names none, zero
     * (decision black-hole-leaves-a-compression-sphere).
     *
     * @param level   the server level
     * @param pos     the struck block
     * @param type    the goo type thrown
     * @param face    the struck face
     * @param ability the ability the goo names
     * @param point   the aimed point the ability resolves at, or null for the cell's center
     * @param size    the cast's size in blocks, zero for a throw
     */
    public static void land(ServerLevel level, BlockPos pos, ResourceKey<GooTypeDefinition> type,
                            Direction face, AbilityDefinition ability, @Nullable Vec3 point, double size) {
        land(level, pos, type, face, ability, point, size, null);
    }

    /**
     * Lands an ability on a block for the player whose goo it was, whom its
     * landing records as its caster
     * (decision quantum-anchors-link-two-points).
     *
     * @param level   the server level
     * @param pos     the struck block
     * @param type    the goo type thrown
     * @param face    the struck face
     * @param ability the ability the goo names
     * @param point   the aimed point the ability resolves at, or null for the cell's center
     * @param size    the cast's size in blocks, zero for a throw
     * @param caster  the player whose goo landed, or null for none
     */
    public static void land(ServerLevel level, BlockPos pos, ResourceKey<GooTypeDefinition> type,
                            Direction face, AbilityDefinition ability, @Nullable Vec3 point, double size,
                            @Nullable UUID caster) {
        Optional<LandingSpot> spot = landingSpot(level, pos, face, point);
        if (spot.isEmpty()) {
            return;
        }
        BlockPos cell = spot.get().cell();
        LandingHost host = new LandingHost(level, cell, spot.get().face(), type,
                ability.id().toString(), anchor(cell, point), size, caster);
        AbilitySplat.resolve(new Landing(host, ability));
    }

    /**
     * Where a landing's world actions anchor: the aimed point where the
     * throw names one, so a free ability resolves where it was aimed, and the
     * landing cell's center otherwise.
     * aim-point-follows-the-cursor
     *
     * @param cell  the landing cell
     * @param point the aimed point, or null for none
     * @return the anchor
     */
    static Vec3 anchor(BlockPos cell, @Nullable Vec3 point) {
        return point == null ? Vec3.atCenterOf(cell) : point;
    }

    /**
     * The cell a blob lands in, the one the placement rules pick.
     *
     * @param level the server level
     * @param pos   the struck block
     * @param face  the struck face
     * @param point the aimed point, or null for none
     * @return the landing, or empty where nothing takes it
     */
    private static Optional<LandingSpot> landingSpot(ServerLevel level, BlockPos pos, Direction face,
                                                     @Nullable Vec3 point) {
        return point == null ? LandingSpot.resolve(level, pos, face) : LandingSpot.resolve(level, pos, face, point);
    }

    /**
     * Whether an ability's blob lights a fire where it lands, the fire it
     * lights standing in for a burnout; the operator's ruling (idea
     * ignite-plays-no-burnout): Ignite's fire is its whole visual.
     *
     * @param ability the landing ability
     * @return true when a top-level step lights the landing's fire
     */
    static boolean lightsItsFire(AbilityDefinition ability) {
        return ability.behaviors().stream().anyMatch(step -> step.type() == LeafSteps.LIGHT_FIRE.type());
    }

    /**
     * Whether an ability calls a meteor down on its target, the falling
     * meteor standing in for a burnout, so the release leaves no mark there.
     * decision meteo-makes-no-mark-on-release
     *
     * @param ability the landing ability
     * @return true when a top-level step calls a meteor
     */
    static boolean callsAMeteor(AbilityDefinition ability) {
        return ability.behaviors().stream().anyMatch(MeteorStep.class::isInstance);
    }

    /**
     * A blob's world actions as it lands.
     *
     * @param host    the landing host
     * @param ability the ability the blob names
     */
    private record Landing(LandingHost host, AbilityDefinition ability) implements AbilitySplat {

        @Override
        public void announceBurnout() {
            new ChainBurnoutPayload(host.cell(), host.face().ordinal(), GooTypes.id(host.gooType()),
                    host.abilityId(), host.size()).sendToTracking(host.level());
        }

        @Override
        public boolean turnsIntoItsBlock() {
            return AbilityImpact.lightsItsFire(ability)
                    || AbilityImpact.callsAMeteor(ability);
        }

        @Override
        public void runProgram() {
            try {
                ProgramBehavior.forHost(ability.behaviors(), HostKind.LANDING).tick(host);
            } catch (ProgramLoadException e) {
                Goo.LOGGER.error(LOG_PROGRAM_REFUSED, ability.id(), e.getMessage());
            }
        }
    }
}
