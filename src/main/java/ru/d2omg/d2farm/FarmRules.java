package ru.d2omg.d2farm;

public final class FarmRules {
    public static final int COMPOST_HARVESTS = 5;
    private FarmRules() { }

    public static double extraGrowthChance(int vanillaBound) {
        return vanillaBound <= 1 ? 0 : 0.5 / (vanillaBound - 1);
    }

    public static int afterHarvest(int charges) {
        return Math.max(0, charges - 1);
    }
}
