package org.geysermc.connector.network.translators.java.entity;

import com.github.steveice10.mc.protocol.data.game.entity.metadata.Position;
import com.github.steveice10.mc.protocol.packet.ingame.server.world.ServerUpdateTileEntityPacket;
import com.nukkitx.math.vector.Vector3i;
import com.nukkitx.nbt.tag.CompoundTag;
import com.nukkitx.protocol.bedrock.packet.BlockEntityDataPacket;
import org.geysermc.connector.network.session.GeyserSession;
import org.geysermc.connector.network.translators.BlockEntityUtils;
import org.geysermc.connector.network.translators.PacketTranslator;

public class JavaUpdateTileEntityPacketTranslator extends PacketTranslator<ServerUpdateTileEntityPacket> {
    @Override
    public void translate(ServerUpdateTileEntityPacket packet, GeyserSession session) {
        BlockEntityDataPacket bedrock = new BlockEntityDataPacket();

        Position pos = packet.getPosition();

        bedrock.setData(BlockEntityUtils.getExtraTags(packet.getNbt()));
        bedrock.setBlockPosition(Vector3i.from(pos.getX(), pos.getY(), pos.getZ()));

        session.getUpstream().sendPacketImmediately(bedrock);
    }
}
