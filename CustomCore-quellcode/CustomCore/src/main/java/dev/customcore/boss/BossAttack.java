package dev.customcore.boss;

import org.bukkit.entity.Player;

import java.util.List;

/** Eine Boss-Attacke. targets ist nach Entfernung sortiert (nächster zuerst) und nie leer. */
@FunctionalInterface
public interface BossAttack {
    void execute(BossInstance boss, List<Player> targets);
}
