package com.splitwise.enums;

/**
 * Defines the four supported expense split strategies.
 *
 * EQUAL      — amount / N, remainder cent goes to the payer
 * UNEQUAL    — client provides exact amounts, server validates sum = total
 * PERCENTAGE — client provides percentages (must sum to 100), server computes amounts
 * SHARE      — client provides share units, server computes proportional amounts
 */
public enum SplitType {
    EQUAL,
    UNEQUAL,
    PERCENTAGE,
    SHARE
}
