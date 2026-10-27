package com.example.voidscape.pet;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundSetPassengersPacket;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import java.util.List;
import java.util.stream.IntStream;

/**
 * Seats a perched pet on its owner's head on a viewer's client only. That client then carries
 * the parts with the owner every frame, so they never trail behind the head, while the server
 * never records a passenger: Paper refuses to teleport a player who carries one, which would
 * break /spawn, warps and portals.
 */
final class PetMount {
    private PetMount(){}

    /** Tells {@code viewer} that {@code parts} ride {@code owner}, keeping any real passengers it has. */
    static void seat(Player viewer,Player owner,List<? extends Entity> parts){
        int[] ids=IntStream.concat(owner.getPassengers().stream().mapToInt(Entity::getEntityId),
            parts.stream().filter(Entity::isValid).mapToInt(Entity::getEntityId)).toArray();
        FriendlyByteBuf buffer=new FriendlyByteBuf(Unpooled.buffer());
        try{
            buffer.writeVarInt(owner.getEntityId());
            buffer.writeVarIntArray(ids);
            ((CraftPlayer)viewer).getHandle().connection.send(ClientboundSetPassengersPacket.STREAM_CODEC.decode(buffer));
        }finally{buffer.release();}
    }
}
