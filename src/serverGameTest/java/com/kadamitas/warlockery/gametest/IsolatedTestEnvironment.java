package com.kadamitas.warlockery.gametest;

import com.kadamitas.warlockery.util.GameTestMockPlayers;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.stream.StreamSupport;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.server.level.ServerLevel;

public record IsolatedTestEnvironment(
    String id,
    Holder<TestEnvironmentDefinition<?>> delegate
) implements TestEnvironmentDefinition<TestEnvironmentDefinition.Activation<?>> {
    public static final MapCodec<IsolatedTestEnvironment> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
        Codec.STRING.fieldOf("id").forGetter(IsolatedTestEnvironment::id),
        TestEnvironmentDefinition.CODEC.fieldOf("delegate").forGetter(IsolatedTestEnvironment::delegate)
    ).apply(instance, IsolatedTestEnvironment::new));

    @Override
    public TestEnvironmentDefinition.Activation<?> setup(final ServerLevel level) {
        return TestEnvironmentDefinition.activate(delegate.value(), level);
    }

    @Override
    public void teardown(
        final ServerLevel level,
        final TestEnvironmentDefinition.Activation<?> activation
    ) {
        activation.teardown();
        if (!(level.getServer() instanceof GameTestServer gameTestServer)) {
            return;
        }
        gameTestServer.getPlayerList().getPlayers().stream()
            .toList()
            .forEach(GameTestMockPlayers::disconnect);
        StreamSupport.stream(level.getAllEntities().spliterator(), false)
            .toList()
            .forEach(entity -> entity.discard());
    }

    @Override
    public MapCodec<IsolatedTestEnvironment> codec() {
        return CODEC;
    }
}
