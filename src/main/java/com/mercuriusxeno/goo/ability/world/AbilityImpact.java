package com.mercuriusxeno.goo.ability.world;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.program.HostKind;
import com.mercuriusxeno.goo.ability.program.LandingHost;
import com.mercuriusxeno.goo.ability.program.ProgramBehavior;
import com.mercuriusxeno.goo.ability.program.ProgramLoadException;
import com.mercuriusxeno.goo.network.ChainBurnoutPayload;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.Optional;

/**
 * What an ability goo does when it lands on a block: it lands in the cell
 * {@link LandingSpot} decides, announces its burnout, and runs its program
 * on that landing the tick it splats. The landing places no block of its
 * own; a program ending that tick leaves only its effect, and one that
 * lingers stands its own block through its linger step (decisions
 * splat-runs-the-program-no-fuse, lingering-abilities-place-their-own-thing).
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
        Optional<LandingSpot> spot = LandingSpot.resolve(level, pos, face);
        if (spot.isEmpty()) {
            return;
        }
        LandingHost host = new LandingHost(level, spot.get().cell(), face, spot.get().waterlogged(), type,
                ability.id().toString());
        AbilitySplat.resolve(new Landing(host, ability));
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
            BlockPos cell = host.cell();
            ChainBurnoutPayload burnout = new ChainBurnoutPayload(cell, host.face().ordinal(),
                    GooTypes.id(host.gooType()), host.abilityId());
            int chunkX = SectionPos.blockToSectionCoord(cell.getX());
            int chunkZ = SectionPos.blockToSectionCoord(cell.getZ());
            // A listener that never negotiated the mod's channels, a gametest's mock player, gets no burnout.
            for (ServerPlayer player : host.level().players()) {
                if (player.getChunkTrackingView().contains(chunkX, chunkZ) && player.connection.hasChannel(burnout)) {
                    PacketDistributor.sendToPlayer(player, burnout);
                }
            }
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
