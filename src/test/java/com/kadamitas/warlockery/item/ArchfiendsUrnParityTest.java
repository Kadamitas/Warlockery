package com.kadamitas.warlockery.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kadamitas.warlockery.brew.BrewKind;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

final class ArchfiendsUrnParityTest {
    @Test
    void storesFourDistinctBrewPropertiesWithoutConsumingThemOnResolve() {
        ArchfiendsUrnState state = ArchfiendsUrnState.empty();
        for (BrewKind brew : List.of(BrewKind.HEAL, BrewKind.WEBS, BrewKind.FEAR, BrewKind.TRANSPOSE)) {
            final ArchfiendsUrnState.AddResult result = state.add(brew);
            assertTrue(result.changed());
            state = result.state();
        }
        assertEquals(ArchfiendsUrnState.CAPACITY, state.brews().size());
        assertEquals(state.brews(), state.resolvedBrews().stream().map(BrewKind::id).toList());
        assertEquals(state.brews(), state.resolvedBrews().stream().map(BrewKind::id).toList());
    }

    @Test
    void reportsDuplicateAndFullUrnsWithoutChangingStoredProperties() {
        final ArchfiendsUrnState one = ArchfiendsUrnState.empty().add(BrewKind.HEAL).state();
        final ArchfiendsUrnState.AddResult duplicate = one.add(BrewKind.HEAL);
        assertFalse(duplicate.changed());
        assertEquals(ArchfiendsUrnState.Diagnostic.ALREADY_STORED, duplicate.diagnostic());
        final ArchfiendsUrnState full = new ArchfiendsUrnState(List.of(
            BrewKind.HEAL.id(), BrewKind.WEBS.id(), BrewKind.FEAR.id(), BrewKind.TRANSPOSE.id()
        ));
        final ArchfiendsUrnState.AddResult overflow = full.add(BrewKind.BLAST);
        assertFalse(overflow.changed());
        assertEquals(ArchfiendsUrnState.Diagnostic.FULL, overflow.diagnostic());
        assertEquals(full, overflow.state());
    }

    @Test
    void persistsOnlyRegisteredBrewProperties() {
        final CompoundTag data = new CompoundTag();
        new ArchfiendsUrnState(List.of(BrewKind.HEAL.id(), BrewKind.WEBS.id())).write(data);
        assertEquals(List.of(BrewKind.HEAL.id(), BrewKind.WEBS.id()), ArchfiendsUrnState.read(data).brews());
        assertTrue(UtilityItemFactory.supports("archfiends_urn"));
    }
}
