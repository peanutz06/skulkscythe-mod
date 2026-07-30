package com.skulkscythe.mod.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Sent client -> server when the player presses the echolocation keybind
 * while holding the Skulk Scythe. Carries no data — the server just needs
 * to know "this player wants to ping".
 */
public record EcholocationPayload() implements CustomPayload {
    public static final CustomPayload.Id<EcholocationPayload> ID =
            new CustomPayload.Id<>(Identifier.of("skulkscythe", "echolocation"));

    public static final PacketCodec<RegistryByteBuf, EcholocationPayload> CODEC =
            PacketCodec.unit(new EcholocationPayload());

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
