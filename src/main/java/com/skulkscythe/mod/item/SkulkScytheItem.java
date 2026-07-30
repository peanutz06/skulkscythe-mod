package com.skulkscythe.mod.item;

import com.skulkscythe.mod.SkulkScytheMod;
import com.skulkscythe.mod.ability.SkulkAbilities;
import com.skulkscythe.mod.util.AbilityCooldowns;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.UseAction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

/**
 * The Skulk Scythe.
 *
 * Right-click (hold):        charge up a Warden-style sonic boom, release to fire.
 * Sneak + Right-click:        dash forward. Never calls addExhaustion -> zero hunger cost.
 *                             If the warden-charge bar is full, this summons a Warden instead.
 * Melee hit:                  shrieks and applies Darkness to the target, adds warden-bar charge.
 * Echolocation:                bound to a keybind (default R) - see SkulkScytheModClient.
 *
 * All client-only particle work (charge swirl, ambient aura) lives in SkulkScytheModClient's
 * client tick handler rather than here, since referencing client-only types (like ClientWorld)
 * from this shared class would break dedicated servers even inside an isClient() check -
 * the JVM resolves referenced types at class-verification time, not just when a branch runs.
 */
public class SkulkScytheItem extends Item {

    // Sonic boom
    public static final int SONIC_BOOM_COOLDOWN_TICKS = 40;      // 2s, on top of the charge time itself
    public static final int MAX_CHARGE_TICKS = 40;               // 2s to fully charge
    public static final double SONIC_BOOM_BASE_RANGE = 10.0;
    public static final double SONIC_BOOM_BONUS_RANGE = 10.0;    // up to 20 blocks at full charge
    public static final float SONIC_BOOM_BASE_DAMAGE = 6.0F;
    public static final float SONIC_BOOM_BONUS_DAMAGE = 10.0F;   // up to 16 damage at full charge
    public static final int SONIC_BOOM_CHARGE_GAIN = 20;         // warden-bar progress per entity hit

    // Dash
    public static final int DASH_COOLDOWN_TICKS = 25;            // 1.25s
    public static final double DASH_STRENGTH = 2.4;

    // Warden summon bar
    public static final int MAX_WARDEN_CHARGE = 100;
    public static final int MELEE_CHARGE_GAIN = 8;

    // Echolocation
    public static final int ECHOLOCATION_COOLDOWN_TICKS = 300;   // 15s

    public SkulkScytheItem(Settings settings) {
        super(settings);
    }

    @Override
    public ActionResult use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);

        if (user.isSneaking()) {
            if (stack.getOrDefault(SkulkScytheMod.WARDEN_CHARGE, 0) >= MAX_WARDEN_CHARGE) {
                SkulkAbilities.summonWarden(world, user, stack);
            } else if (AbilityCooldowns.isReady(user, "dash", DASH_COOLDOWN_TICKS)) {
                SkulkAbilities.dash(world, user);
                AbilityCooldowns.use(user, "dash");
            }
            return ActionResult.SUCCESS;
        }

        if (!AbilityCooldowns.isReady(user, "sonic_boom", SONIC_BOOM_COOLDOWN_TICKS)) {
            return ActionResult.FAIL;
        }

        // Start charging - onStoppedUsing() fires the actual boom when the player releases.
        return ActionResult.CONSUME;
    }

    @Override
    public int getMaxUseTime(ItemStack stack) {
        return MAX_CHARGE_TICKS + 20; // small buffer past full charge
    }

    @Override
    public UseAction getUseAction(ItemStack stack) {
        return UseAction.SPEAR;
    }

    @Override
    public void usageTick(World world, LivingEntity user, ItemStack stack, int remainingUseTicks) {
        if (world.isClient()) return; // client-side charge visuals live in SkulkScytheModClient

        int used = getMaxUseTime(stack) - remainingUseTicks;

        // Rising-pitch heartbeat pulses at 25/50/75%, a roar right at full charge.
        if (used == MAX_CHARGE_TICKS / 4 || used == MAX_CHARGE_TICKS / 2
                || used == (MAX_CHARGE_TICKS * 3) / 4) {
            world.playSound(null, user.getBlockPos(), SoundEvents.ENTITY_WARDEN_HEARTBEAT,
                    SoundCategory.PLAYERS, 1.0F, 0.6F + (used / (float) MAX_CHARGE_TICKS));
        } else if (used == MAX_CHARGE_TICKS) {
            world.playSound(null, user.getBlockPos(), SoundEvents.ENTITY_WARDEN_ROAR,
                    SoundCategory.PLAYERS, 1.0F, 1.2F);
        }

        // Continuous charging "whine" - a sculk sensor click every 4 ticks, pitch rising with charge.
        if (used % 4 == 0) {
            float pitch = 0.6F + Math.min(used, MAX_CHARGE_TICKS) / (float) MAX_CHARGE_TICKS * 1.3F;
            world.playSound(null, user.getBlockPos(), SoundEvents.BLOCK_SCULK_SENSOR_CLICKING,
                    SoundCategory.PLAYERS, 0.6F, pitch);
        }
    }

    @Override
    public boolean onStoppedUsing(ItemStack stack, World world, LivingEntity user, int remainingUseTicks) {
        if (!(user instanceof PlayerEntity player)) return true;

        int used = getMaxUseTime(stack) - remainingUseTicks;
        float ratio = MathHelper.clamp(used / (float) MAX_CHARGE_TICKS, 0.15F, 1.0F);

        SkulkAbilities.fireSonicBoom(world, player, ratio);
        AbilityCooldowns.use(player, "sonic_boom");
        return true;
    }

    @Override
    public void postHit(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        SkulkAbilities.shriek(stack, target, attacker);
    }

    @Override
    public void inventoryTick(ItemStack stack, World world, Entity entity, int slot, boolean selected) {
        if (world.isClient()) return; // client-only aura/glow visuals live in SkulkScytheModClient
        if (!(entity instanceof PlayerEntity player)) return;
        boolean holding = selected || player.getOffHandStack() == stack;
        if (!holding) return;

        if (world.getTime() % 160 == 0 && world.random.nextFloat() < 0.5F) {
            // Faint ambient hum every so often - kept rare so it doesn't get grating.
            world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_WARDEN_AMBIENT,
                    SoundCategory.PLAYERS, 0.4F, 0.7F);
        }
    }

    // ---------- Warden charge bar (renders like the bundle fill indicator) ----------

    @Override
    public boolean isItemBarVisible(ItemStack stack) {
        return stack.getOrDefault(SkulkScytheMod.WARDEN_CHARGE, 0) > 0;
    }

    @Override
    public int getItemBarStep(ItemStack stack) {
        int charge = stack.getOrDefault(SkulkScytheMod.WARDEN_CHARGE, 0);
        return Math.round(13.0F * MathHelper.clamp(charge, 0, MAX_WARDEN_CHARGE) / MAX_WARDEN_CHARGE);
    }

    @Override
    public int getItemBarColor(ItemStack stack) {
        int charge = stack.getOrDefault(SkulkScytheMod.WARDEN_CHARGE, 0);
        float t = MathHelper.clamp(charge, 0, MAX_WARDEN_CHARGE) / (float) MAX_WARDEN_CHARGE;

        // Interpolate from skulk teal to warden purple as the bar fills.
        int r = (int) MathHelper.lerp(t, 68, 145);
        int g = (int) MathHelper.lerp(t, 220, 60);
        int b = (int) MathHelper.lerp(t, 230, 235);
        return (r << 16) | (g << 8) | b;
    }
}
