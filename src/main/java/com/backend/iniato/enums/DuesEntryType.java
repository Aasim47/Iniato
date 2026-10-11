package com.backend.iniato.enums;

/**
 * Why a driver's dues balance moved.
 */
public enum DuesEntryType {
    /** A completed ride added the platform's cut to what the driver owes. */
    ACCRUAL,
    /** The driver settled up — reduces the balance. */
    PAYMENT,
    /** Manual correction by support. */
    ADJUSTMENT
}
