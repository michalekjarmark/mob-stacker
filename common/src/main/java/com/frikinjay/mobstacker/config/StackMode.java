package com.frikinjay.mobstacker.config;

/**
 * Controls where mob stacking is allowed to happen.
 * <ul>
 *     <li>{@link #OFF} - stacking never forms new stacks.</li>
 *     <li>{@link #REGIONS} - stacking only forms inside an ALLOW region (and never inside a DENY region).</li>
 *     <li>{@link #EVERYWHERE} - stacking forms everywhere, except inside a DENY region.</li>
 * </ul>
 */
public enum StackMode {
    OFF,
    REGIONS,
    EVERYWHERE
}
