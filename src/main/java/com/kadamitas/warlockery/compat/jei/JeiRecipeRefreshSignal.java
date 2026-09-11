package com.kadamitas.warlockery.compat.jei;

import com.kadamitas.warlockery.compat.viewer.RecipeViewerRefreshSignal;

public final class JeiRecipeRefreshSignal {
    private JeiRecipeRefreshSignal() { }
    public static void subscribe(final Runnable listener) { RecipeViewerRefreshSignal.subscribe(listener); }
    public static void publish() { RecipeViewerRefreshSignal.publish(); }
}
