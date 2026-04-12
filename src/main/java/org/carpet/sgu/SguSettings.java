package org.carpet.sgu;

import carpet.api.settings.Rule;

import static carpet.api.settings.RuleCategory.EXPERIMENTAL;
import static carpet.api.settings.RuleCategory.FEATURE;



public class SguSettings {

    public static final String SGU = "SGU";

    @Rule(
            categories = {FEATURE, EXPERIMENTAL,SGU}
    )
    public static boolean betterFakePlayerProcess = false;

    @Rule(
            categories = {FEATURE, EXPERIMENTAL,SGU}
    )
    public static boolean reverseBlockPosTraversal = false;
}
