package com.kadamitas.warlockery.entity;

@FunctionalInterface
public interface AmbientActivity {
    boolean perform(AmbientActivityContext context);
}
