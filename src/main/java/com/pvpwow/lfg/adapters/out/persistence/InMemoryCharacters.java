package com.pvpwow.lfg.adapters.out.persistence;

import com.pvpwow.lfg.application.port.out.Characters;
import com.pvpwow.lfg.domain.character.Character;
import com.pvpwow.lfg.domain.character.CharacterName;
import com.pvpwow.lfg.domain.character.Realm;
import com.pvpwow.lfg.domain.character.Region;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * BOOTSTRAP stand-in for the JPA adapter (next step): keeps everything in
 * memory so the app runs end to end before persistence exists. Deliberately
 * naive — nothing queries it back yet, nothing survives a restart — and the
 * plan is to REPLACE it, never to grow it.
 */
@Component
final class InMemoryCharacters implements Characters {

    private final Set<String> registeredKeys = ConcurrentHashMap.newKeySet();
    private final List<Character> saved = Collections.synchronizedList(new ArrayList<>());

    @Override
    public void save(Character character) {
        saved.add(character);
        registeredKeys.add(key(character.region(), character.realm(), character.name()));
    }

    @Override
    public boolean alreadyRegistered(Region region, Realm realm, CharacterName name) {
        return registeredKeys.contains(key(region, realm, name));
    }

    private String key(Region region, Realm realm, CharacterName name) {
        return "%s|%s|%s".formatted(region, realm.value(), name.value());
    }
}
