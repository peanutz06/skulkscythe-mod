package com.skulkscythe.mod;

import com.skulkscythe.mod.item.SkulkScytheItem;
import com.skulkscythe.mod.network.EcholocationPayload;
import com.skulkscythe.mod.util.AbilityCooldowns;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

/**
 * Client-only bits: the "echolocation" keybind (default: R, press while
 * holding the scythe to ping), a small cooldown HUD, and every particle
 * effect that's purely visual (charge-up swirl, ambient aura).
 *
 * These particle effects live here rather than in SkulkScytheItem because
 * this class is only ever loaded on the client - referencing a client-only
 * type like ClientWorld from the shared Item class would break dedicated
 * servers even inside an isClient() check, since the JVM resolves referenced
 * types at class-verification time, not just when a branch actually runs.
 */
public class SkulkScytheModClient implements ClientModInitializer {

    private static KeyBinding echolocationKey;

    @Override
    public void onInitializeClient() {
        KeyBinding.Category category = KeyBinding.Category.create(Identifier.of("skulkscythe", "main"));

        echolocationKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.skulkscythe.echolocation",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_R,
                category
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            ClientPlayerEntity player = client.player;
            if (player == null) return;

            while (echolocationKey.wasPressed()) {
                if (isHoldingScythe(player)) {
                    ClientPlayNetworking.send(new EcholocationPayload());
                    // Optimistic local cooldown so the HUD updates instantly,
                    // ahead of the server round-trip.
                    AbilityCooldowns.use(player, "echolocation");
                }
            }

            tickClientVisuals(client, player);
        });

        HudRenderCallback.EVENT.register((drawContext, tickCounter) -> renderCooldownHud(drawContext));
    }

    private boolean isHoldingScythe(ClientPlayerEntity player) {
        return player.getMainHandStack().getItem() instanceof SkulkScytheItem
                || player.getOffHandStack().getItem() instanceof SkulkScytheItem;
    }

    // ---------- Client-only particle visuals ----------

    private void tickClientVisuals(MinecraftClient client, ClientPlayerEntity player) {
        if (!(client.world instanceof ClientWorld world)) return;
        if (!isHoldingScythe(player)) return;

        long t = world.getTime();

        // Continuous swirling sculk aura - two counter-rotating rings around the player.
        if (t % 2 == 0) {
            double angle = t * 0.12;
            double radius = 0.8;
            double y = player.getY() + 0.1 + Math.sin(t * 0.08) * 0.5 + 0.9;

            double x1 = player.getX() + Math.cos(angle) * radius;
            double z1 = player.getZ() + Math.sin(angle) * radius;
            world.addParticle(ParticleTypes.SCULK_SOUL, x1, y, z1, 0, 0.01, 0);

            double x2 = player.getX() + Math.cos(angle + Math.PI) * radius;
            double z2 = player.getZ() + Math.sin(angle + Math.PI) * radius;
            world.addParticle(ParticleTypes.SCULK_SOUL, x2, y, z2, 0, 0.01, 0);
        }

        // Occasional crackle spark somewhere around the player.
        if (t % 15 == 0) {
            world.addParticle(ParticleTypes.SCULK_CHARGE_POP,
                    player.getX() + (world.random.nextDouble() - 0.5) * 1.2,
                    player.getY() + world.random.nextDouble() * 1.8,
                    player.getZ() + (world.random.nextDouble() - 0.5) * 1.2,
                    0, 0, 0);
        }

        // Extra restless glow once the warden bar is fully charged.
        ItemStack mainHand = player.getMainHandStack();
        ItemStack stack = mainHand.getItem() instanceof SkulkScytheItem ? mainHand : player.getOffHandStack();
        int charge = stack.getOrDefault(SkulkScytheMod.WARDEN_CHARGE, 0);
        if (charge >= SkulkScytheItem.MAX_WARDEN_CHARGE && t % 10 == 0) {
            world.addParticle(ParticleTypes.SCULK_SOUL,
                    player.getX() + (world.random.nextDouble() - 0.5),
                    player.getY() + 1.0 + world.random.nextDouble() * 0.5,
                    player.getZ() + (world.random.nextDouble() - 0.5),
                    0, 0.05, 0);
        }

        // Charging swirl while actively holding right-click to charge the sonic boom.
        if (player.isUsingItem() && player.getActiveItem().getItem() instanceof SkulkScytheItem) {
            int maxUse = ((SkulkScytheItem) player.getActiveItem().getItem()).getMaxUseTime(player.getActiveItem());
            int remaining = player.getItemUseTimeLeft();
            int used = maxUse - remaining;

            if (used % 2 == 0) {
                double frac = Math.min(used, SkulkScytheItem.MAX_CHARGE_TICKS)
                        / (double) SkulkScytheItem.MAX_CHARGE_TICKS;
                double radius = 0.5 + frac * 0.8;
                double y = player.getY() + 0.9 + Math.sin(used * 0.3) * 0.3;

                double angle = used * 0.5;
                double x = player.getX() + Math.cos(angle) * radius;
                double z = player.getZ() + Math.sin(angle) * radius;
                world.addParticle(ParticleTypes.SCULK_SOUL, x, y, z, 0, 0.02, 0);

                double angle2 = -used * 0.5 + Math.PI;
                double x2 = player.getX() + Math.cos(angle2) * radius;
                double z2 = player.getZ() + Math.sin(angle2) * radius;
                world.addParticle(ParticleTypes.SCULK_CHARGE_POP, x2, y, z2, 0, 0.02, 0);
            }
        }
    }

    // ---------- Cooldown HUD ----------

    private void renderCooldownHud(DrawContext drawContext) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        if (player == null || client.options.hudHidden) return;
        if (!isHoldingScythe(player)) return;

        int x = 8;
        int y = client.getWindow().getScaledHeight() / 2 - 30;

        drawAbilityIcon(drawContext, client, player, x, y,
                new ItemStack(SkulkScytheMod.SKULK_SCYTHE), "sonic_boom", SkulkScytheItem.SONIC_BOOM_COOLDOWN_TICKS);
        y += 20;
        drawAbilityIcon(drawContext, client, player, x, y,
                new ItemStack(Items.FEATHER), "dash", SkulkScytheItem.DASH_COOLDOWN_TICKS);
        y += 20;
        drawAbilityIcon(drawContext, client, player, x, y,
                new ItemStack(Items.SCULK_SENSOR), "echolocation", SkulkScytheItem.ECHOLOCATION_COOLDOWN_TICKS);
    }

    private void drawAbilityIcon(DrawContext ctx, MinecraftClient client, ClientPlayerEntity player,
                                  int x, int y, ItemStack iconStack, String ability, int cooldownTicks) {
        ctx.drawItem(iconStack, x, y);

        float progress = AbilityCooldowns.progress(player, ability, cooldownTicks); // 0 = just used, 1 = ready
        if (progress < 1f) {
            int coveredHeight = Math.round((1f - progress) * 16f);
            ctx.fill(x, y, x + 16, y + coveredHeight, 0xA0000000);

            int secondsLeft = (int) Math.ceil((1f - progress) * cooldownTicks / 20f);
            if (secondsLeft > 0) {
                ctx.drawText(client.textRenderer, String.valueOf(secondsLeft), x + 19, y + 4, 0xFFFFFF, true);
            }
        }
    }
}
