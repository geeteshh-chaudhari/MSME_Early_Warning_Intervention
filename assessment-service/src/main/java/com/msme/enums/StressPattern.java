
package com.msme.enums;

public enum StressPattern {

	 BROAD_FINANCIAL_DETERIORATION,
	    COLLECTION_LIQUIDITY_STRESS,
	    CREDIT_DEPENDENCY_STRESS,

	    // Compatibility with existing sample database records.
	    SEASONAL_DROP,
	    RECEIVABLE_SPIKE,
	    CREDIT_OVERUSE,
	    STABLE_GROWTH;

    public static StressPattern determine(
            boolean revenueBreached,
            boolean receivablesBreached,
            boolean creditBreached) {

        // Highest precedence: all three
        if (revenueBreached
                && receivablesBreached
                && creditBreached) {

            return BROAD_FINANCIAL_DETERIORATION;
        }

        // Receivables combined with another breach
        if (receivablesBreached
                && (revenueBreached || creditBreached)) {

            return COLLECTION_LIQUIDITY_STRESS;
        }

        // Revenue and credit
        if (revenueBreached && creditBreached) {
            return CREDIT_DEPENDENCY_STRESS;
        }

        // No matching pattern
        return null;
    }
}
