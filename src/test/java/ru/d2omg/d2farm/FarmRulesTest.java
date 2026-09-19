package ru.d2omg.d2farm;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FarmRulesTest {
    @Test void rainProbabilityIsExactlyFiftyPercentHigher() {
        for (int bound = 2; bound <= 26; bound++) {
            double base = 1.0 / bound;
            assertEquals(base * 1.5, base + (1 - base) * FarmRules.extraGrowthChance(bound), 1e-12);
        }
    }
    @Test void noBonusWhenGrowthAlreadyGuaranteed() { assertEquals(0, FarmRules.extraGrowthChance(1)); }
    @Test void compostLastsFiveHarvestsAndNeverGoesNegative() {
        int charges = FarmRules.COMPOST_HARVESTS;
        for (int i = 4; i >= 0; i--) assertEquals(i, charges = FarmRules.afterHarvest(charges));
        assertEquals(0, FarmRules.afterHarvest(charges));
    }
}
