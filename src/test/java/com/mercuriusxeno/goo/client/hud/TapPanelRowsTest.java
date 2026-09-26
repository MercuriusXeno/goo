package com.mercuriusxeno.goo.client.hud;

import com.mercuriusxeno.goo.block.tap.TapDripGrade;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The tap valve panel reads each grade's rate, labelled by the grade itself,
 * and off while the valve is closed.
 */
class TapPanelRowsTest {

    private static final Map<TapDripGrade, String> EXPECTED = Map.of(
            TapDripGrade.ONE_PER_64_TICKS, "1 mB/3.2 s",
            TapDripGrade.ONE_PER_16_TICKS, "1.25 mB/s",
            TapDripGrade.ONE_PER_4_TICKS, "5 mB/s",
            TapDripGrade.ONE_PER_TICK, "20 mB/s",
            TapDripGrade.FOUR_PER_TICK, "80 mB/s");

    private static String onlyText(List<PanelRow> rows) {
        assertEquals(1, rows.size());
        return rows.getFirst().segments().getFirst().text();
    }

    @Test
    void eachGradeReadsItsRate() {
        for (TapDripGrade grade : TapDripGrade.values()) {
            assertEquals(EXPECTED.get(grade), onlyText(TapPanelRows.rows(Optional.of(grade))), grade.name());
            assertEquals(EXPECTED.get(grade), grade.rateLabel(), grade.name());
        }
    }

    @Test
    void aClosedValveReadsOff() {
        assertEquals("off", onlyText(TapPanelRows.rows(Optional.empty())));
    }
}
