package org.carpet.sgu;

import carpet.api.settings.Rule;

import static carpet.api.settings.RuleCategory.EXPERIMENTAL;
import static carpet.api.settings.RuleCategory.FEATURE;

public class SguSettings {
    public static final String SGU = "sgu_addition";

    @Rule(
            categories = {SGU, FEATURE, EXPERIMENTAL}
    )
    public static boolean betterFakePlayerProcess = false;
}
