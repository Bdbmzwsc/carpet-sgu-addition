package org.carpet.sgu;

import carpet.api.settings.Rule;

import static carpet.api.settings.RuleCategory.EXPERIMENTAL;
import static carpet.api.settings.RuleCategory.FEATURE;

public class SguSettings {
    @Rule(
            categories = {FEATURE, EXPERIMENTAL}
    )
    public static boolean betterFakePlayerProcess = false;
}
