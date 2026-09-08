package cn.ykntd.game;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CoreBossBarColorPolicyTest {
    @Test
    void warningRangeAvoidsBetterHudReservedYellowChannel() {
        assertEquals(CoreBossBarColorPolicy.Tone.GREEN,
                CoreBossBarColorPolicy.tone(0.51f));
        assertEquals(CoreBossBarColorPolicy.Tone.PURPLE,
                CoreBossBarColorPolicy.tone(0.50f));
        assertEquals(CoreBossBarColorPolicy.Tone.PURPLE,
                CoreBossBarColorPolicy.tone(0.251f));
        assertEquals(CoreBossBarColorPolicy.Tone.RED,
                CoreBossBarColorPolicy.tone(0.25f));
        assertEquals(CoreBossBarColorPolicy.Tone.RED,
                CoreBossBarColorPolicy.tone(Float.NaN));
    }
}
