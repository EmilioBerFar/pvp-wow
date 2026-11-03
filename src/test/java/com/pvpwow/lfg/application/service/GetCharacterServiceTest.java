package com.pvpwow.lfg.application.service;

import com.pvpwow.lfg.application.error.ResourceNotFoundException;
import com.pvpwow.lfg.application.port.out.Characters;
import com.pvpwow.lfg.domain.character.Character;
import com.pvpwow.lfg.domain.character.CharacterId;
import com.pvpwow.lfg.domain.character.CharacterName;
import com.pvpwow.lfg.domain.character.GearSnapshot;
import com.pvpwow.lfg.domain.character.ItemLevel;
import com.pvpwow.lfg.domain.character.Realm;
import com.pvpwow.lfg.domain.character.Region;
import com.pvpwow.lfg.domain.character.Specialization;
import com.pvpwow.lfg.domain.character.WowClass;
import com.pvpwow.lfg.domain.player.PlayerId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

class GetCharacterServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-02T10:00:00Z");
    private static final PlayerId OWNER = new PlayerId(UUID.randomUUID());
    private static final CharacterId CHARACTER_ID = new CharacterId(UUID.randomUUID());

    private final FakeCharacters characters = new FakeCharacters();
    private final GetCharacterService service = new GetCharacterService(characters, () -> OWNER);

    @Test
    void returnsTheCallersOwnCharacter() {
        Character character = characterOwnedBy(OWNER);
        characters.store(character);

        assertThat(service.get(CHARACTER_ID)).isSameAs(character);
    }

    @Test
    void anUnknownIdAndAnotherPlayersCharacterFailWithTheSameMessage() {
        ResourceNotFoundException unknown = catchThrowableOfType(
                () -> service.get(CHARACTER_ID), ResourceNotFoundException.class);

        characters.store(characterOwnedBy(new PlayerId(UUID.randomUUID())));
        ResourceNotFoundException foreign = catchThrowableOfType(
                () -> service.get(CHARACTER_ID), ResourceNotFoundException.class);

        assertThat(unknown.getMessage()).isEqualTo("Character not found");
        assertThat(foreign.getMessage())
                .as("a foreign character must not reveal that it exists")
                .isEqualTo(unknown.getMessage());
    }

    @Test
    void theCallerIsResolvedBeforeAnyLookup() {
        GetCharacterService unauthenticated = new GetCharacterService(characters,
                () -> {
                    throw new IllegalStateException("no caller");
                });

        assertThatThrownBy(() -> unauthenticated.get(CHARACTER_ID))
                .as("an unauthenticated call fails even for an id that does not exist")
                .isInstanceOf(IllegalStateException.class);
    }

    private static Character characterOwnedBy(PlayerId owner) {
        return Character.rehydrate(CHARACTER_ID, owner, new CharacterName("Frostbolt"),
                new Realm("sanguino"), Region.EU, WowClass.MAGE,
                new GearSnapshot(new ItemLevel(675), NOW),
                Set.of(Specialization.MAGE_FROST), Map.of());
    }

    private static final class FakeCharacters implements Characters {

        private Character stored;

        void store(Character character) {
            stored = character;
        }

        @Override
        public void save(Character character) {
            stored = character;
        }

        @Override
        public Optional<Character> findById(CharacterId id) {
            return Optional.ofNullable(stored).filter(character -> character.id().equals(id));
        }

        @Override
        public boolean alreadyRegistered(Region region, Realm realm, CharacterName name) {
            return false;
        }
    }
}
