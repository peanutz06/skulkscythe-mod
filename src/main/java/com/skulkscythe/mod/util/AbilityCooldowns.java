package com.skulkscythe.mod.util;

import net.minecraft.entity.player.PlayerEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * A tiny in-memory cooldown tracker, keyed by player UUID + ability name.
 *
 * We roll our own instead of using vanilla's ItemCooldownManager because that
 * manager keys cooldowns off the Item itself — since sonic boom, dash, and
 * echolocation all live on the same item, sharing one cooldown bucket would
 * mean using one ability locks out the others. This keeps them independent.
 *
 * Note: this is in-memory only, so cooldowns reset on server restart / relog.
 * That's an intentional simplification — fine for cooldowns measured in seconds.
 */
public final class AbilityCooldowns {
    private static final Map<UUID, Map<String, Long>> LAST_USE = new HashMap<>();

    private AbilityCooldowns() {
    }

    public static boolean isReady(PlayerEntity player, String ability, int cooldownTicks) {
        long now = player.getEntityWorld().getTime();
        Map<String, Long> perAbility = LAST_USE.get(player.getUuid());
        if (perAbility == null) return true;
        Long last = perAbility.get(ability);
        return last == null || (now - last) >= cooldownTicks;
    }

    public static void use(PlayerEntity player, String ability) {
        long now = player.getEntityWorld().getTime();
        LAST_USE.computeIfAbsent(player.getUuid(), id -> new HashMap<>()).put(ability, now);
    }

    /** 0.0 = just used, 1.0 = fully off cooldown. Handy for HUD/particle intensity. */
    public static float progress(PlayerEntity player, String ability, int cooldownTicks) {
        long now = player.getEntityWorld().getTime();
        Map<String, Long> perAbility = LAST_USE.get(player.getUuid());
        if (perAbility == null) return 1f;
        Long last = perAbility.get(ability);
        if (last == null) return 1f;
        float elapsed = now - last;
        return Math.min(1f, elapsed / (float) cooldownTicks);
    }
}
