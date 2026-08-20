package com.kadamitas.warlockery.entity;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonParser;
import java.nio.file.*;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class EntResourceTest {
    private static final Set<String> FIXTURES=Set.of(
        "ent_felling_rouses_warns_then_strikes_within_its_stand","ent_ignores_presence_and_settles_to_its_anchor",
        "ent_stand_alarm_and_log_break_spawn_stay_bounded","ent_grove_tending_is_bounded_and_respects_mobgriefing",
        "ent_hazard_escape_and_cancellation_are_deterministic","ent_save_reload_variants_and_golem_lifecycle_are_replaced");
    @Test void exactIsolatedDescriptorsExist(){for(String id:FIXTURES){var json=JsonParser.parseString(read("src/main/resources/data/warlockery/test_instance/"+id+".json")).getAsJsonObject();assertEquals("warlockery:"+id,json.get("function").getAsString());assertEquals("warlockery:ent_isolated",json.get("environment").getAsString());assertEquals("forge:empty3x3x3",json.get("structure").getAsString());}}
    @Test void isolatedEnvironmentIsAnEmptyAllOf(){var json=JsonParser.parseString(read("src/main/resources/data/warlockery/test_environment/ent_isolated.json")).getAsJsonObject();assertEquals("minecraft:all_of",json.get("type").getAsString());assertTrue(json.getAsJsonArray("definitions").isEmpty());}
    @Test void dedicatedSourceHasNoGolemOrGenericDispatch(){String source=read("src/main/java/com/kadamitas/warlockery/entity/EntEntity.java");assertFalse(source.contains("extends IronGolem"));assertFalse(source.contains("TacticalCombatRuntime"));assertFalse(source.contains("AmbientActivityRuntime.tick"));assertFalse(source.contains("HazardEscapeRuntime"));assertFalse(source.contains("BonemealableBlock"));}
    private static String read(String path){try{return Files.readString(Path.of(path));}catch(java.io.IOException e){throw new java.io.UncheckedIOException(e);}}
}
