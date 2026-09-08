package cn.ykntd.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PowerStageTest {
    @Test
    void tenWaveCampaignUsesThreeStableStages() {
        assertAll(
                () -> assertEquals(PowerStage.EARLY, PowerStage.forWave(1)),
                () -> assertEquals(PowerStage.EARLY, PowerStage.forWave(3)),
                () -> assertEquals(PowerStage.MID, PowerStage.forWave(4)),
                () -> assertEquals(PowerStage.MID, PowerStage.forWave(7)),
                () -> assertEquals(PowerStage.LATE, PowerStage.forWave(8)),
                () -> assertEquals(PowerStage.LATE, PowerStage.forWave(10)),
                () -> assertEquals(PowerStage.LATE, PowerStage.parse(" late ", 1)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> PowerStage.parse("impossible", 1))
        );
    }
}
