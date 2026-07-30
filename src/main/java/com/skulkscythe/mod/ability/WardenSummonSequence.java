package com.skulkscythe.mod.ability;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.WardenEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * A staged, ~4-second "cinematic" summoning ritual: a circle grows on the
 * ground, chain-like particles rise and clink around its rim, the earth
 * cracks, everything goes quiet for a beat — then a flash, a roar, and the
 * Warden itself appears.
 *
 * This chases the general "epic ritual summon" feeling common across a lot
 * of fantasy media (circle, rising chains, buildup, flash-cut reveal) using
 * our own sculk/warden particles and sounds — it's not attempting to
 * reproduce any specific show's exact scene, character design, or audio.
 *
 * Implementation note: Minecraft has no built-in "wait N ticks then run this"
 * callback, so this runs as a tiny state machine ticked once per world tick
 * (see SkulkScytheMod's ServerTickEvents.END_WORLD_TICK registration), with
 * each stage keyed off elapsed-tick ranges.
 */
public final class WardenSummonSequence {

    private static final int TOTAL_TICKS = 80; // 4 seconds

    private static final class Pending {
        final Vec3d pos;
        final UUID ownerId;
        final float yaw;
        int t = 0;

        Pending(Vec3d pos, UUID ownerId, float yaw) {
            this.pos = pos;
            this.ownerId = ownerId;
            this.yaw = yaw;
        }
    }

    private static final Map<ServerWorld, List<Pending>> QUEUES = new HashMap<>();
    private static final Set<UUID> ACTIVE_OWNERS = new HashSet<>();

    private WardenSummonSequence() {
    }

    public static boolean isActiveFor(PlayerEntity owner) {
        return ACTIVE_OWNERS.contains(owner.getUuid());
    }

    public static void begin(ServerWorld world, Vec3d pos, PlayerEntity owner) {
        if (!ACTIVE_OWNERS.add(owner.getUuid())) return; // already mid-summon, ignore

        Pending pending = new Pending(pos, owner.getUuid(), owner.getYaw());
        QUEUES.computeIfAbsent(world, w -> new ArrayList<>()).add(pending);

        world.playSound(null, BlockPos.ofFloored(pos), SoundEvents.ENTITY_WARDEN_LISTENING,
                SoundCategory.HOSTILE, 3.0F, 0.4F);
        owner.sendMessage(Text.literal("The sculk begins to stir...")
                .formatted(Formatting.DARK_AQUA, Formatting.ITALIC), true);
    }

    public static void tick(ServerWorld world) {
        List<Pending> queue = QUEUES.get(world);
        if (queue == null || queue.isEmpty()) return;

        Iterator<Pending> it = queue.iterator();
        while (it.hasNext()) {
            Pending p = it.next();
            p.t++;
            runStage(world, p);
            if (p.t >= TOTAL_TICKS) {
                ACTIVE_OWNERS.remove(p.ownerId);
                it.remove();
            }
        }
    }

    private static void runStage(ServerWorld world, Pending p) {
        Vec3d pos = p.pos;

        // Stage 1 (ticks 0-30): the ritual circle grows, chain-like particles rise around its rim.
        if (p.t <= 30) {
            double ringRadius = 1.0 + (p.t / 30.0) * 3.0;
            double angle = p.t * 0.5;
            for (int i = 0; i < 3; i++) {
                double a = angle + (i * Math.PI * 2 / 3);
                double x = pos.x + Math.cos(a) * ringRadius;
                double z = pos.z + Math.sin(a) * ringRadius;
                world.spawnParticles(ParticleTypes.SCULK_SOUL, x, pos.y + 0.1, z, 1, 0, 0, 0, 0);
                if (p.t % 4 == 0) {
                    world.spawnParticles(ParticleTypes.SCULK_CHARGE_POP, x, pos.y + 0.1, z, 1, 0, 0.05, 0, 0);
                }
            }
            if (p.t % 6 == 0) {
                world.playSound(null, BlockPos.ofFloored(pos), SoundEvents.BLOCK_CHAIN_PLACE,
                        SoundCategory.HOSTILE, 1.5F, 0.6F + (float) (p.t / 60.0));
            }
        }

        // Stage 2 (ticks 25-50): the ground cracks, dust drags inward toward the center.
        if (p.t >= 25 && p.t <= 50) {
            for (int i = 0; i < 2; i++) {
                double a = world.random.nextDouble() * Math.PI * 2;
                double r = 2.5 + world.random.nextDouble() * 2.0;
                double x = pos.x + Math.cos(a) * r;
                double z = pos.z + Math.sin(a) * r;
                world.spawnParticles(ParticleTypes.CRIT, x, pos.y + 0.2, z, 1,
                        (pos.x - x) * 0.02, 0.02, (pos.z - z) * 0.02, 0);
            }
            if (p.t % 8 == 0) {
                world.playSound(null, BlockPos.ofFloored(pos), SoundEvents.ENTITY_WARDEN_HEARTBEAT,
                        SoundCategory.HOSTILE, 2.0F, 0.5F);
            }
        }

        // Stage 3 (tick 55): a beat of near-silence before the reveal.
        if (p.t == 55) {
            world.playSound(null, BlockPos.ofFloored(pos), SoundEvents.ENTITY_WARDEN_AGITATED,
                    SoundCategory.HOSTILE, 1.0F, 0.4F);
        }

        // Stage 4 (tick 60): flash, roar, reveal - the Warden actually spawns here, not at trigger time.
        if (p.t == 60) {
            world.spawnParticles(ParticleTypes.END_ROD, pos.x, pos.y + 1, pos.z, 1, 0, 0, 0, 0);
            world.spawnParticles(ParticleTypes.SONIC_BOOM, pos.x, pos.y + 1, pos.z, 1, 0, 0, 0, 0);
            world.spawnParticles(ParticleTypes.SCULK_SOUL, pos.x, pos.y + 1, pos.z, 60, 1.5, 1.5, 1.5, 0.06);
            world.spawnParticles(ParticleTypes.SCULK_CHARGE_POP, pos.x, pos.y + 1, pos.z, 30, 1.5, 1.0, 1.5, 0.02);

            world.playSound(null, BlockPos.ofFloored(pos), SoundEvents.ENTITY_WARDEN_EMERGE,
                    SoundCategory.HOSTILE, 4.0F, 1.0F);
            world.playSound(null, BlockPos.ofFloored(pos), SoundEvents.ENTITY_WARDEN_ROAR,
                    SoundCategory.HOSTILE, 4.0F, 0.8F);

            WardenEntity warden = new WardenEntity(EntityType.WARDEN, world);
            warden.refreshPositionAndAngles(pos.x, pos.y, pos.z, p.yaw, 0.0F);
            world.spawnEntity(warden);
            FriendlyWardenTracker.track(warden.getUuid(), p.ownerId);
        }

        // Stage 5 (ticks 61-80): the ring collapses inward as dust settles.
        if (p.t > 60) {
            double frac = (p.t - 60) / 20.0;
            double ringRadius = 4.0 * (1.0 - frac);
            double angle = -p.t * 0.4;
            for (int i = 0; i < 2; i++) {
                double a = angle + (i * Math.PI);
                double x = pos.x + Math.cos(a) * ringRadius;
                double z = pos.z + Math.sin(a) * ringRadius;
                world.spawnParticles(ParticleTypes.SCULK_SOUL, x, pos.y + 0.2, z, 1, 0, 0.02, 0, 0);
            }
        }
    }
}
