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

package org.geysermc.connector.network.session.cache;

import com.nukkitx.protocol.bedrock.packet.ModalFormRequestPacket;
import lombok.Getter;
import org.geysermc.connector.network.session.GeyserSession;
import org.geysermc.api.window.FormWindow;

import java.util.HashMap;
import java.util.Map;

public class WindowCache {

    private GeyserSession session;

    @Getter
    private Map<Integer, FormWindow> windows = new HashMap<Integer, FormWindow>();

    public WindowCache(GeyserSession session) {
        this.session = session;
    }

    public void addWindow(FormWindow window) {
        windows.put(windows.size() + 1, window);
    }

    public void addWindow(FormWindow window, int id) {
        windows.put(id, window);
    }

    public void showWindow(FormWindow window) {
        showWindow(window, windows.size() + 1);
    }

    public void showWindow(int id) {
        if (!windows.containsKey(id))
            return;

        ModalFormRequestPacket formRequestPacket = new ModalFormRequestPacket();
        formRequestPacket.setFormId(id);
        formRequestPacket.setFormData(windows.get(id).getJSONData());

        session.getUpstream().sendPacket(formRequestPacket);
    }

    public void showWindow(FormWindow window, int id) {
        ModalFormRequestPacket formRequestPacket = new ModalFormRequestPacket();
        formRequestPacket.setFormId(id);
        formRequestPacket.setFormData(window.getJSONData());

        session.getUpstream().sendPacket(formRequestPacket);

        addWindow(window, id);
    }
}
