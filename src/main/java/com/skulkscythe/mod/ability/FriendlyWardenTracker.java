package com.skulkscythe.mod.ability;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.WardenEntity;
import net.minecraft.server.world.ServerWorld;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Makes summoned Wardens "friendly" toward whoever summoned them, without
 * needing a whole custom entity subclass / renderer / attribute registration.
 *
 * Every world tick, for each tracked Warden: if it's currently targeting its
 * owner, that target is vetoed (cleared). If it has no target at all, it'll
 * pick up whatever last attacked its owner — so in practice it behaves like a
 * bodyguard: safe to stand next to, but still lashes out at anything that
 * angers it or attacks you.
 *
 * This is a lightweight approximation, not real vanilla taming — the Warden
 * still senses vibrations/darkness normally, we just clear its target back
 * off its owner every tick.
 */
public final class FriendlyWardenTracker {
    private static final Map<UUID, UUID> WARDEN_TO_OWNER = new ConcurrentHashMap<>();

    private FriendlyWardenTracker() {
    }

    public static void track(UUID wardenId, UUID ownerId) {
        WARDEN_TO_OWNER.put(wardenId, ownerId);
    }

    public static void tick(ServerWorld world) {
        if (WARDEN_TO_OWNER.isEmpty()) return;

        Iterator<Map.Entry<UUID, UUID>> it = WARDEN_TO_OWNER.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, UUID> entry = it.next();

            Entity wardenEntity = world.getEntity(entry.getKey());
            if (!(wardenEntity instanceof WardenEntity warden) || !warden.isAlive()) {
                it.remove();
                continue;
            }

            Entity ownerEntity = world.getEntity(entry.getValue());
            if (!(ownerEntity instanceof LivingEntity owner) || !owner.isAlive()) {
                continue; // owner offline/dead/elsewhere - leave the warden be, don't untrack yet
            }

            LivingEntity target = warden.getTarget();
            if (target == owner) {
                warden.setTarget(null);
                target = null;
            }

            if (target == null) {
                LivingEntity ownerAttacker = owner.getAttacker();
                if (ownerAttacker != null && ownerAttacker != warden && ownerAttacker.isAlive()) {
                    warden.setTarget(ownerAttacker);
                }
            }
        }
    }
}
