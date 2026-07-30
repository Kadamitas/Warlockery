package com.kadamitas.warlockery.client;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kadamitas.warlockery.item.ManualProfile;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class ManualArticleCatalogTest {
    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void everyRitualArticleLoadsItsEffectIngredientsConditionsAndDiagram() {
        final ManualProfile circles = ManualProfile.find("ingredient_book_circle_magic").orElseThrow();
        circles.sections().stream().filter(section -> section.startsWith("rite_")).forEach(section -> {
            final ManualArticleCatalog.Article article = ManualArticleCatalog.article(circles, section);
            assertFalse(article.body().getString().isBlank(), section);
            assertTrue(article.hasDiagram(), section);
        });
    }

    @Test
    void everyBrewArticleLoadsItsEffectAndOrderedRecipe() {
        final ManualProfile codex = ManualProfile.find("cauldronbook").orElseThrow();
        codex.sections().stream().filter(section -> section.startsWith("brew_entry_")).forEach(section -> {
            final String text = ManualArticleCatalog.article(codex, section).body().getString();
            assertTrue(text.contains("manual.warlockery.entry.ingredients"), section);
            assertTrue(text.contains("manual.warlockery.entry.workings"), section);
        });
    }
}
