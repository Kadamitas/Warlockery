package com.kadamitas.warlockery.ritual.marriage;

import com.kadamitas.warlockery.Warlockery;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

public final class MarriageData extends SavedData {
    private static final Codec<MarriageData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Bond.CODEC.listOf().optionalFieldOf("bonds", List.of()).forGetter(data -> List.copyOf(data.bonds.values()))
    ).apply(instance, MarriageData::new));
    public static final SavedDataType<MarriageData> TYPE = new SavedDataType<>(
        Identifier.fromNamespaceAndPath(Warlockery.MOD_ID, "marriages"),
        MarriageData::new,
        CODEC,
        DataFixTypes.SAVED_DATA_COMMAND_STORAGE
    );

    private final Map<UUID, Bond> bonds;

    public MarriageData() {
        bonds = new HashMap<>();
    }

    private MarriageData(final List<Bond> saved) {
        bonds = saved.stream().collect(Collectors.toMap(Bond::playerUuid, bond -> bond, (first, _) -> first, HashMap::new));
    }

    public static MarriageData get(final ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    public Optional<Bond> bond(final UUID playerId) {
        return Optional.ofNullable(bonds.get(playerId));
    }

    public boolean isMarried(final UUID playerId) {
        return bonds.containsKey(playerId);
    }

    public MarriageResult marryPlayers(final UUID first, final UUID second) {
        if (first.equals(second)) {
            return MarriageResult.INVALID_PARTNER;
        }
        if (isMarried(first) || isMarried(second)) {
            return MarriageResult.ALREADY_MARRIED;
        }
        bonds.put(first, Bond.player(first, second));
        bonds.put(second, Bond.player(second, first));
        setDirty();
        return MarriageResult.SUCCESS;
    }

    public MarriageResult marryNami(final UUID playerId, final UUID namiId) {
        if (isMarried(playerId) || ownerForNami(namiId).isPresent()) {
            return MarriageResult.ALREADY_MARRIED;
        }
        final Set<String> claimed = bonds.values().stream()
            .filter(Bond::isNami)
            .map(Bond::spouseName)
            .collect(Collectors.toUnmodifiableSet());
        final Optional<String> name = DemonSpouseNames.firstAvailable(claimed);
        if (name.isEmpty()) {
            return MarriageResult.NO_DEMON_NAMES;
        }
        bonds.put(playerId, Bond.nami(playerId, namiId, name.orElseThrow()));
        setDirty();
        return MarriageResult.SUCCESS;
    }

    public boolean hasAvailableDemonName() {
        final Set<String> claimed = bonds.values().stream()
            .filter(Bond::isNami)
            .map(Bond::spouseName)
            .collect(Collectors.toUnmodifiableSet());
        return DemonSpouseNames.firstAvailable(claimed).isPresent();
    }

    public Optional<UUID> ownerForNami(final UUID namiId) {
        return bonds.values().stream()
            .filter(Bond::isNami)
            .filter(bond -> bond.partnerUuid().equals(namiId))
            .map(Bond::playerUuid)
            .findFirst();
    }

    public Optional<Bond> divorce(final UUID playerId) {
        final Bond removed = bonds.remove(playerId);
        if (removed == null) {
            return Optional.empty();
        }
        if (removed.isPlayer()) {
            bonds.remove(removed.partnerUuid());
        }
        setDirty();
        return Optional.of(removed);
    }

    public enum MarriageResult {
        SUCCESS,
        INVALID_PARTNER,
        ALREADY_MARRIED,
        NO_DEMON_NAMES
    }

    public record Bond(String playerId, String partnerId, String partnerType, String spouseName) {
        private static final Codec<Bond> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("player_id").forGetter(Bond::playerId),
            Codec.STRING.fieldOf("partner_id").forGetter(Bond::partnerId),
            Codec.STRING.fieldOf("partner_type").forGetter(Bond::partnerType),
            Codec.STRING.optionalFieldOf("spouse_name", "").forGetter(Bond::spouseName)
        ).apply(instance, Bond::new));

        public static Bond player(final UUID playerId, final UUID partnerId) {
            return new Bond(playerId.toString(), partnerId.toString(), "player", "");
        }

        public static Bond nami(final UUID playerId, final UUID partnerId, final String spouseName) {
            return new Bond(playerId.toString(), partnerId.toString(), "nami", spouseName);
        }

        public UUID playerUuid() {
            return UUID.fromString(playerId);
        }

        public UUID partnerUuid() {
            return UUID.fromString(partnerId);
        }

        public boolean isPlayer() {
            return "player".equals(partnerType);
        }

        public boolean isNami() {
            return "nami".equals(partnerType);
        }
    }
}
