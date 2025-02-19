/*
 * Copyright (c) 2019 GeyserMC. http://geysermc.org
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 *
 * @author GeyserMC
 * @link https://github.com/GeyserMC/Geyser
 */

package org.geysermc.connector.network.translators.java.entity.spawn;

import com.github.steveice10.mc.protocol.packet.ingame.server.entity.spawn.ServerSpawnMobPacket;
import com.nukkitx.math.vector.Vector3f;
import org.geysermc.connector.console.GeyserLogger;
import org.geysermc.connector.entity.Entity;
import org.geysermc.connector.entity.type.EntityType;
import org.geysermc.connector.network.session.GeyserSession;
import org.geysermc.connector.network.translators.PacketTranslator;
import org.geysermc.connector.utils.EntityUtils;

public class JavaSpawnMobTranslator extends PacketTranslator<ServerSpawnMobPacket> {

    @Override
    public void translate(ServerSpawnMobPacket packet, GeyserSession session) {
        Vector3f position = Vector3f.from(packet.getX(), packet.getY(), packet.getZ());
        Vector3f motion = Vector3f.from(packet.getMotionX(), packet.getMotionY(), packet.getMotionZ());
        Vector3f rotation = Vector3f.from(packet.getYaw(), packet.getPitch(), packet.getHeadYaw());

        EntityType type = EntityUtils.toBedrockEntity(packet.getType());
        if (type == null) {
            GeyserLogger.DEFAULT.warning("Entity type " + packet.getType() + " was null.");
            return;
        }

        Entity entity = new Entity(
                packet.getEntityId(), session.getEntityCache().getNextEntityId().incrementAndGet(),
                type, position, motion, rotation
        );

        session.getEntityCache().spawnEntity(entity);
    }
}
