package dev.customcore.ability;

/** Eine ausführbare Fähigkeit einer Waffe (Hit-Effekt oder Rechtsklick). */
@FunctionalInterface
public interface Ability {
    void execute(AbilityContext ctx);
}
