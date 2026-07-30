package com.skulkscythe.mod;

import com.mojang.serialization.Codec;
import com.skulkscythe.mod.ability.FriendlyWardenTracker;
import com.skulkscythe.mod.ability.SkulkAbilities;
import com.skulkscythe.mod.ability.WardenSummonSequence;
import com.skulkscythe.mod.item.SkulkScytheItem;
import com.skulkscythe.mod.network.EcholocationPayload;
import com.skulkscythe.mod.util.AbilityCooldowns;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.component.ComponentType;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;

public class SkulkScytheMod implements ModInitializer {
    public static final String MOD_ID = "skulkscythe";

    public static final Item SKULK_SCYTHE = new SkulkScytheItem(
            new Item.Settings().maxCount(1).maxDamage(0)
    );

    /**
     * Stores the Warden-summon bar progress (0..SkulkScytheItem.MAX_WARDEN_CHARGE)
     * directly on the item stack, so it persists with the item and drives the
     * built-in item-bar rendering (see SkulkScytheItem#getItemBarStep etc).
     */
    public static final ComponentType<Integer> WARDEN_CHARGE = Registry.register(
            Registries.DATA_COMPONENT_TYPE,
            Identifier.of(MOD_ID, "warden_charge"),
            ComponentType.<Integer>builder().codec(Codec.INT).build()
    );

    @Override
    public void onInitialize() {
        Registry.register(Registries.ITEM, Identifier.of(MOD_ID, "skulk_scythe"), SKULK_SCYTHE);

        ItemGroupEvents.modifyEntriesEvent(ItemGroups.COMBAT).register(entries -> entries.add(SKULK_SCYTHE));

        // Keeps summoned Wardens from targeting whoever summoned them.
        ServerTickEvents.END_WORLD_TICK.register(FriendlyWardenTracker::tick);
        // Runs the staged "cinematic" summon ritual (circle, chains, crack, flash, reveal).
        ServerTickEvents.END_WORLD_TICK.register(WardenSummonSequence::tick);

        // /sculk - gives the Skulk Scythe to whoever runs it.
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(CommandManager.literal("sculk").executes(context -> {
                    ServerCommandSource source = context.getSource();
                    ServerPlayerEntity player = source.getPlayerOrThrow();
                    ItemStack stack = new ItemStack(SKULK_SCYTHE);
                    if (!player.giveItemStack(stack)) {
                        player.dropItem(stack, false);
                    }
                    source.sendFeedback(() -> Text.literal("Here's your Skulk Scythe.")
                            .formatted(Formatting.DARK_AQUA), false);
                    return 1;
                }))
        );

        // Networking: client -> server "I pressed the echolocation key" ping.
        PayloadTypeRegistry.playC2S().register(EcholocationPayload.ID, EcholocationPayload.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(EcholocationPayload.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            ServerWorld serverWorld = (ServerWorld) player.getEntityWorld();
            serverWorld.getServer().execute(() -> {
                ItemStack mainHand = player.getMainHandStack();
                ItemStack offHand = player.getOffHandStack();
                ItemStack stack = mainHand.getItem() instanceof SkulkScytheItem ? mainHand : offHand;
                if (!(stack.getItem() instanceof SkulkScytheItem)) return;

                if (AbilityCooldowns.isReady(player, "echolocation", SkulkScytheItem.ECHOLOCATION_COOLDOWN_TICKS)) {
                    SkulkAbilities.echolocation(player);
                    AbilityCooldowns.use(player, "echolocation");
                }
            });
        });
    }
}
