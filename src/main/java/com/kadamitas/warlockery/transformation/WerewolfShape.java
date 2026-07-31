package com.kadamitas.warlockery.transformation;

import com.kadamitas.warlockery.util.EnumLookup;
import com.kadamitas.warlockery.util.StringIdentified;
import java.util.Locale;

public enum WerewolfShape implements StringIdentified {
    HUMAN,
    WOLF,
    WOLFMAN;

    private static final EnumLookup<WerewolfShape> LOOKUP = EnumLookup.create("werewolf shape", values());

    @Override
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static WerewolfShape parse(final String value) {
        return LOOKUP.findOrElse(value.toLowerCase(Locale.ROOT), HUMAN);
    }
}
