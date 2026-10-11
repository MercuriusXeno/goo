package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.ability.AbilityJson;
import com.mercuriusxeno.goo.type.GooTypes;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The ender tap rolls a chance on each drip and convokes on a win
 * (decision convoke-drip-rolls-a-small-chance).
 */
class ConvokeTapProgramTest {

    private static final int DRIPS = 200;

    private static List<Step> program() {
        return AbilityJson.decode("ender_convoke_tap").behaviors();
    }

    private static BranchStep roll() {
        return (BranchStep) program().getFirst();
    }

    @Test
    void theTapProgramLoadsForTheTapHost() {
        assertDoesNotThrow(() -> ProgramBehavior.forHost(program(), HostKind.TAP));
    }

    @Test
    void theChanceStartsAtFivePercent() {
        assertEquals("chance(0.05)", roll().when().source());
    }

    @Test
    void aChanceOfZeroNeverConvokes() {
        ConvokeHost host = mock(ConvokeHost.class);
        when(host.kind()).thenReturn(HostKind.TAP);
        BranchStep losing = new BranchStep(Expr.parse("chance(0)").getOrThrow(), roll().then(), roll().otherwise());
        for (int drip = 0; drip < DRIPS; drip++) {
            new ProgramBehavior(List.of(losing)).tick(host);
        }
        verify(host, never()).convokeFromChunk(GooTypes.ENDER);
    }

    @Test
    void aWinningDripConvokes() {
        ConvokeHost host = mock(ConvokeHost.class);
        when(host.kind()).thenReturn(HostKind.TAP);
        BranchStep certain = new BranchStep(Expr.literal(1), roll().then(), roll().otherwise());
        new ProgramBehavior(List.of(certain)).tick(host);
        verify(host).convokeFromChunk(GooTypes.ENDER);
    }
}
