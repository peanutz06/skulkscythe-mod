package com.skulkscythe.mod.ability;

import com.skulkscythe.mod.SkulkScytheMod;
import com.skulkscythe.mod.item.SkulkScytheItem;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.List;

/**
 * All the Skulk Scythe's active/passive abilities live here so both the item
 * class (melee hits, right-click) and the network handler (echolocation
 * keybind) can call into the same logic.
 */
public final class SkulkAbilities {

    private SkulkAbilities() {
    }

    // ---------- Sonic boom ----------

    public static void fireSonicBoom(World world, PlayerEntity user, float chargeRatio) {
        chargeRatio = MathHelper.clamp(chargeRatio, 0.15f, 1.0f);

        double range = SkulkScytheItem.SONIC_BOOM_BASE_RANGE + chargeRatio * SkulkScytheItem.SONIC_BOOM_BONUS_RANGE;
        float damage = SkulkScytheItem.SONIC_BOOM_BASE_DAMAGE + chargeRatio * SkulkScytheItem.SONIC_BOOM_BONUS_DAMAGE;

        Vec3d start = user.getEyePos();
        Vec3d look = user.getRotationVector().normalize();

        if (!world.isClient() && world instanceof ServerWorld serverWorld) {
            world.playSound(null, user.getBlockPos(), SoundEvents.ENTITY_WARDEN_SONIC_BOOM,
                    SoundCategory.PLAYERS, 2.5F + chargeRatio, 0.9F + chargeRatio * 0.3F);

            Box sweep = new Box(start, start).expand(1.0 + chargeRatio).stretch(look.multiply(range));
            List<Entity> hit = world.getOtherEntities(user, sweep,
                    e -> e instanceof LivingEntity && e != user);

            ItemStack stack = user.getMainHandStack().getItem() instanceof SkulkScytheItem
                    ? user.getMainHandStack() : user.getOffHandStack();

            for (Entity entity : hit) {
                Vec3d toEntity = entity.getEntityPos().subtract(start);
                if (toEntity.length() > range) continue;
                if (toEntity.normalize().dotProduct(look) < 0.75) continue;

                if (entity instanceof LivingEntity living) {
                    living.damage(serverWorld, world.getDamageSources().sonicBoom(user), damage);
                    Vec3d knockback = toEntity.normalize().multiply(2.0 + chargeRatio * 1.5).add(0, 0.4, 0);
                    entity.setVelocity(entity.getVelocity().add(knockback));
                    entity.velocityDirty = true;

                    addWardenCharge(stack, SkulkScytheItem.SONIC_BOOM_CHARGE_GAIN, user);
                }
            }

            spawnRing(serverWorld, start, 1.2 + chargeRatio * 0.8, 16);

            int rings = chargeRatio >= 0.99f ? 2 : 1;
            for (int r = 0; r < rings; r++) {
                for (double d = 1; d < range; d += 1.0) {
                    Vec3d point = start.add(look.multiply(d));
                    serverWorld.spawnParticles(ParticleTypes.SONIC_BOOM, point.x, point.y, point.z, 1, 0, 0, 0, 0);
                    if (d % 3 == 0) {
                        serverWorld.spawnParticles(ParticleTypes.SCULK_CHARGE_POP, point.x, point.y, point.z,
                                2, 0.3, 0.3, 0.3, 0.01);
                    }
                }
            }
        }

        user.swingHand(Hand.MAIN_HAND);
    }

    // ---------- Dash ----------

    public static void dash(World world, PlayerEntity user) {
        Vec3d look = user.getRotationVector();
        Vec3d dashDir = new Vec3d(look.x, Math.max(look.y, 0.1), look.z).normalize();
        Vec3d dashVec = dashDir.multiply(SkulkScytheItem.DASH_STRENGTH);

        // Deliberately never calling addExhaustion(...) anywhere in this method -> zero hunger cost.
        user.setVelocity(user.getVelocity().add(dashVec.x, dashVec.y * 0.4, dashVec.z));
        user.velocityDirty = true;
        user.fallDistance = 0;

        if (!world.isClient()) {
            world.playSound(null, user.getBlockPos(), SoundEvents.ENTITY_WARDEN_STEP,
                    SoundCategory.PLAYERS, 1.0F, 1.7F);

            if (world instanceof ServerWorld serverWorld) {
                Vec3d pos = user.getEntityPos();
                serverWorld.spawnParticles(ParticleTypes.SCULK_SOUL, pos.x, pos.y + 0.8, pos.z,
                        16, 0.35, 0.8, 0.35, 0.02);
                serverWorld.spawnParticles(ParticleTypes.SCULK_CHARGE_POP, pos.x, pos.y + 0.6, pos.z,
                        8, 0.45, 0.6, 0.45, 0.01);
            }
        }
    }

    // ---------- Sculk shriek (on melee hit) ----------

    public static void shriek(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        World world = target.getEntityWorld();
        if (world.isClient()) return;

        target.addStatusEffect(new StatusEffectInstance(StatusEffects.DARKNESS, 140, 0, false, true, true));
        world.playSound(null, target.getBlockPos(), SoundEvents.BLOCK_SCULK_SHRIEKER_SHRIEK,
                SoundCategory.HOSTILE, 1.3F, 1.0F);

        if (world instanceof ServerWorld serverWorld) {
            Vec3d pos = target.getEntityPos();
            serverWorld.spawnParticles(ParticleTypes.SCULK_SOUL,
                    pos.x, target.getEyeY() - 0.3, pos.z, 10, 0.3, 0.3, 0.3, 0.02);
        }

        if (attacker instanceof PlayerEntity player) {
            addWardenCharge(stack, SkulkScytheItem.MELEE_CHARGE_GAIN, player);
        }
    }

    // ---------- Echolocation ----------

    public static void echolocation(ServerPlayerEntity player) {
        World world = player.getEntityWorld();
        double radius = 24.0;
        Box area = player.getBoundingBox().expand(radius);

        List<LivingEntity> found = world.getEntitiesByClass(LivingEntity.class, area,
                e -> e != player && e.isAlive());

        for (LivingEntity e : found) {
            e.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 100, 0, false, false, true));
        }

        world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_WARDEN_LISTENING_ANGRY,
                SoundCategory.PLAYERS, 2.0F, 0.6F);

        if (world instanceof ServerWorld serverWorld) {
            Vec3d center = player.getEntityPos();
            serverWorld.spawnParticles(ParticleTypes.SCULK_SOUL, center.x, center.y + 1, center.z,
                    30, 0.2, 0.4, 0.2, 0.01);
            spawnRing(serverWorld, center, 4.0, 24);
            spawnRing(serverWorld, center, 8.0, 32);
            spawnRing(serverWorld, center, 12.0, 40);
            for (double h = 0; h < 3.0; h += 0.4) {
                serverWorld.spawnParticles(ParticleTypes.SCULK_CHARGE_POP, center.x, center.y + h, center.z,
                        1, 0.1, 0, 0.1, 0);
            }
        }

        player.sendMessage(Text.literal("You sense " + found.size() + " nearby creature"
                + (found.size() == 1 ? "" : "s") + " through the sculk.").formatted(Formatting.DARK_AQUA), true);
    }

    private static void spawnRing(ServerWorld world, Vec3d center, double radius, int count) {
        for (int i = 0; i < count; i++) {
            double angle = (2 * Math.PI / count) * i;
            double x = center.x + Math.cos(angle) * radius;
            double z = center.z + Math.sin(angle) * radius;
            world.spawnParticles(ParticleTypes.SONIC_BOOM, x, center.y + 0.1, z, 1, 0, 0, 0, 0);
        }
    }

    // ---------- Warden charge bar ----------

    public static void addWardenCharge(ItemStack stack, int amount, PlayerEntity player) {
        if (!(stack.getItem() instanceof SkulkScytheItem)) return;

        int current = stack.getOrDefault(SkulkScytheMod.WARDEN_CHARGE, 0);
        int max = SkulkScytheItem.MAX_WARDEN_CHARGE;
        int updated = Math.min(max, current + amount);
        stack.set(SkulkScytheMod.WARDEN_CHARGE, updated);

        if (updated >= max && current < max && !player.getEntityWorld().isClient()) {
            player.sendMessage(Text.literal("The Skulk Scythe is fully charged. "
                    + "Sneak + right-click to summon a Warden.").formatted(Formatting.DARK_AQUA), true);
            player.getEntityWorld().playSound(null, player.getBlockPos(), SoundEvents.ENTITY_WARDEN_HEARTBEAT,
                    SoundCategory.PLAYERS, 2.0F, 1.0F);
        }
    }

    // ---------- Warden summon ----------

    public static void summonWarden(World world, PlayerEntity user, ItemStack stack) {
        if (world.isClient()) return;
        if (!(world instanceof ServerWorld serverWorld)) return;
        if (WardenSummonSequence.isActiveFor(user)) return; // already mid-ritual, ignore extra triggers

        Vec3d pos = user.getEntityPos().add(user.getRotationVector().multiply(3));

        // Reset the bar immediately so it can't be re-triggered mid-ritual; the
        // actual Warden appears a few seconds later, at the climax of the sequence.
        stack.set(SkulkScytheMod.WARDEN_CHARGE, 0);

        WardenSummonSequence.begin(serverWorld, pos, user);
    }
}
