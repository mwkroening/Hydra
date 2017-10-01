package de.datasec.hydra.shared.protocol;

import de.datasec.hydra.shared.handler.HydraSession;
import de.datasec.hydra.shared.protocol.packets.HydraPacketListener;
import de.datasec.hydra.shared.protocol.packets.Packet;
import de.datasec.hydra.shared.protocol.packets.PacketHandler;
import de.datasec.hydra.shared.protocol.packets.PacketId;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by DataSec on 29.09.2017.
 */
public class HydraProtocol {

    private Map<Byte, Class<? extends Packet>> packets = new HashMap<>();

    private Map<Class<? extends Packet>, Byte> packetBytes = new HashMap<>();

    private Map<Class<?>, Method> packetListenerMethods = new HashMap<>();

    private HydraPacketListener packetListener;

    private HydraSession session;

    public void registerPacket(Class<? extends Packet> clazz) {
        if(clazz == null) {
            throw new IllegalArgumentException("clazz can't be null!");
        }

        PacketId packetId = clazz.getAnnotation(PacketId.class);

        if (packetId == null) {
            throw new NullPointerException(String.format("Annotation of packet %s.class not found. Annotation might not be defined!", clazz.getSimpleName()));
        }

        byte id = packetId.value();

        if (packets.containsKey(id)) {
            throw new IllegalArgumentException(String.format("Packet with id %s is already registered!", id));
        }

        packets.put(id, clazz);
        packetBytes.put(clazz, id);
    }

    public Packet createPacket(byte id) {
        try {
            return packets.get(id).newInstance();
        } catch (InstantiationException | IllegalAccessException e) {
            System.err.printf("Packet %s.class might hasn't got an empty constructor!%n\n", packets.get(id).getSimpleName());
            e.printStackTrace();
        }

        return null;
    }

    public byte getPacketId(Packet packet) {
        return packetBytes.get(packet.getClass());
    }

    public void registerListener(HydraPacketListener packetListener) {
        if (packetListener == null) {
            throw new IllegalArgumentException("packetListener can't be null!");
        }

        this.packetListener = packetListener;

        for (Method method : packetListener.getClass().getMethods()) {
            if (method.isAnnotationPresent(PacketHandler.class)) {
                if (method.getParameterCount() == 2) {
                    Class clazz = method.getParameterTypes()[0];
                    if (Packet.class.isAssignableFrom(clazz)) {
                        if (!packetListenerMethods.containsKey(clazz)) {
                            packetListenerMethods.put(clazz, method);
                        } else {
                            throw new IllegalArgumentException(String.format("It's not possible to assign multiple PacketHandler methods for packet %s.class", clazz.getSimpleName()));
                        }
                    } else {
                        throw new IllegalArgumentException(String.format("%s is not a deriving class of Packet.class. Make sure the first argument is a deriving class of Packet.class. And the first argument of the PacketHandler method is the packet itself!", clazz.getSimpleName()));
                    }
                } else {
                    throw new IllegalArgumentException("There are just 2 arguments allowed for a PacketHandler method!");
                }
            }
        }
    }

    public void callListener(Packet packet) {
        try {
            packetListenerMethods.get(packet.getClass()).invoke(packetListener, packet.getClass().cast(packet), session);
        } catch (IllegalAccessException | InvocationTargetException e) {
            e.printStackTrace();
        }
    }

    public void setSession(HydraSession session) {
        this.session = session;
    }
}