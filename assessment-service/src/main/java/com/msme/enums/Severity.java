
package com.msme.enums;

public enum Severity {

    HEALTHY(0),
    WATCH(1),
    EARLY_STRESS(2),
    HIGH_PRIORITY(3);

    private final int breachedCount;

    Severity(int breachedCount) {
        this.breachedCount = breachedCount;
    }

    public int getBreachedCount() {
        return breachedCount;
    }

    public static Severity fromBreachedCount(int count) {

        return switch (count) {
            case 0 -> HEALTHY;
            case 1 -> WATCH;
            case 2 -> EARLY_STRESS;
            case 3 -> HIGH_PRIORITY;
            default -> throw new IllegalArgumentException(
                "Breached indicator count must be between 0 and 3"
            );
        };
    }
}
