package cn.ykntd.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PartyPowerProfileTest {
    private static final double EPSILON = 1.0e-9;

    @Test
    void unavailableProfileDoesNotChangeDifficulty() {
        PartyPowerProfile profile = PartyPowerProfile.unavailable();

        assertAll(
                () -> assertFalse(profile.available()),
                () -> assertEquals(1.0, profile.healthMultiplier(100), EPSILON),
                () -> assertEquals(1.0, profile.damageMultiplier(100), EPSILON),
                () -> assertEquals("未接入", profile.displayValue())
        );
    }

    @Test
    void correctionIsBoundedAroundWaveReference() {
        PartyPowerProfile strong = new PartyPowerProfile(true, 100.0, 3);
        PartyPowerProfile weak = new PartyPowerProfile(true, 0.0, 2);
        PartyPowerProfile matched = new PartyPowerProfile(true, 50.0, 1);

        assertAll(
                () -> assertEquals(Math.pow(AuraPowerIndex.referenceOffenseFactor(100), 0.65),
                        strong.healthMultiplier(0), EPSILON),
                () -> assertEquals(1.20, strong.damageMultiplier(0), EPSILON),
                () -> assertEquals(Math.pow(1.0 / AuraPowerIndex.referenceOffenseFactor(100), 0.65),
                        weak.healthMultiplier(100), EPSILON),
                () -> assertEquals(0.85, weak.damageMultiplier(100), EPSILON),
                () -> assertEquals(1.0, matched.healthMultiplier(50), EPSILON),
                () -> assertEquals(1.0, matched.damageMultiplier(50), EPSILON),
                () -> assertTrue(strong.available())
        );
    }

    @Test
    void offenseAndDefenseAdjustIndependentMonsterAxes() {
        PartyPowerProfile glassCannon = new PartyPowerProfile(true, 100.0, 0.0, 1);
        PartyPowerProfile tank = new PartyPowerProfile(true, 0.0, 100.0, 1);

        assertAll(
                () -> assertTrue(glassCannon.healthMultiplier(50) > 1.0),
                () -> assertTrue(glassCannon.damageMultiplier(50) < 1.0),
                () -> assertTrue(tank.healthMultiplier(50) < 1.0),
                () -> assertTrue(tank.damageMultiplier(50) > 1.0)
        );
    }

    @Test
    void invalidSamplesNormalizeToUnavailableAndIndexesClamp() {
        PartyPowerProfile notFinite = new PartyPowerProfile(true, Double.NaN, 1);
        PartyPowerProfile noSamples = new PartyPowerProfile(true, 50.0, 0);
        PartyPowerProfile high = new PartyPowerProfile(true, 500.0, 1);
        PartyPowerProfile low = new PartyPowerProfile(true, -5.0, 1);

        assertAll(
                () -> assertFalse(notFinite.available()),
                () -> assertFalse(noSamples.available()),
                () -> assertEquals(150.0, high.auraIndex(), EPSILON),
                () -> assertEquals(0.0, low.auraIndex(), EPSILON),
                () -> assertTrue(high.healthMultiplier(80) > 1.0),
                () -> assertTrue(low.damageMultiplier(20) < 1.0),
                () -> assertTrue(high.healthMultiplier(-100) <= 1.25),
                () -> assertEquals(0.85, low.damageMultiplier(500), EPSILON)
        );
    }
}
