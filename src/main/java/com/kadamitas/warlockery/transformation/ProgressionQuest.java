package com.kadamitas.warlockery.transformation;

import java.util.Set;

interface ProgressionQuest<A extends Enum<A>> {
    int targetLevel();

    Set<A> abilities();
}
