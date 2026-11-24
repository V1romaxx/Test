# CustomCore (Paper 1.21.11, Java 21)

Custom-Waffen mit Hit-Effekten und Rechtsklick-Fähigkeiten plus ein Boss-Framework
für bestehende Mobs mit neuen Attacken, Phasen und Drops.

## Installation
1. `mvn package` ausführen (Maven + Java 21) -> `target/CustomCore-1.0.0.jar`
2. Jar in den `plugins/`-Ordner deines Paper-1.21.11-Servers legen, Server starten.
3. Konfiguration liegt danach in `plugins/CustomCore/weapons.yml` und `bosses.yml`.

## Befehle (Permission: customcore.weapon / customcore.boss, Standard: OP)
- `/weapon give [spieler] <id> [anzahl]` - Waffe geben
- `/weapon list` - alle Waffen anzeigen
- `/weapon reload` - weapons.yml und bosses.yml neu laden
- `/boss spawn <id>` - Boss an deiner Position spawnen
- `/boss list`, `/boss killall`, `/boss reload`

## Neue Waffe
Eintrag in `weapons.yml` anlegen (Beispiele und alle Ability-Typen stehen dort), dann `/weapon reload`.
- `on-hit`: Effekte beim Treffen (mit optionalem `cooldown` und `chance` pro Ability)
- `on-right-click`: Fähigkeiten per Rechtsklick (mit `cooldown`)
- `item-model`: optional für Resource Packs

## Neuer Boss
Eintrag in `bosses.yml`: beliebiger existierender Mob (`type`) + Attackenliste, Phasen, Equipment, Drops.
Bosse überleben Neustarts (werden per PersistentDataContainer wiedererkannt).

## Eigene Attacken / Abilities per Code (eigenes Plugin mit `depend: [CustomCore]`)
```java
CustomCorePlugin core = CustomCorePlugin.get();

// Neue Waffen-Ability, nutzbar in weapons.yml als "type: meteor"
core.abilities().register("meteor", cfg -> {
    double damage = cfg.getDouble("damage", 10);
    return ctx -> { /* ctx.user(), ctx.target(), ctx.origin() ... */ };
});

// Neue Boss-Attacke, nutzbar in bosses.yml als "type: meteor_rain"
core.bosses().registerAttackType("meteor_rain", cfg -> (boss, targets) -> {
    // boss.entity(), targets.get(0) = nächster Spieler
});
core.bosses().reload(); // damit bosses.yml die neuen Typen kennt

// Code-Hooks für einen bestimmten Boss
core.bosses().registerBehavior("zombie_king", new BossBehavior() {
    @Override public void onPhase(BossInstance boss, BossDefinition.Phase phase) { /* ... */ }
});
```

## Hinweise
- Rechtsklick-Waffen brechen die normale Rechtsklick-Aktion ab (kein Blockplatzieren/Essen mit der Waffe in der Hand).
- Waffen werden per NBT-Tag erkannt; Umbenennen im Amboss ändert die Funktion nicht.
