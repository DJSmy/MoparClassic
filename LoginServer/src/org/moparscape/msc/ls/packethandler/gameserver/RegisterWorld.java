package org.moparscape.msc.ls.packethandler.gameserver;

import java.util.Arrays;

import org.apache.mina.common.IoSession;
import org.moparscape.msc.ls.Server;
import org.moparscape.msc.ls.model.World;
import org.moparscape.msc.ls.net.LSPacket;
import org.moparscape.msc.ls.net.Packet;
import org.moparscape.msc.ls.packetbuilder.gameserver.WorldRegisteredPacketBuilder;
import org.moparscape.msc.ls.packethandler.PacketHandler;
import org.moparscape.msc.ls.util.Config;
import org.moparscape.msc.ls.util.DataConversions;
import org.moparscape.msc.ls.util.Hash;

public class RegisterWorld implements PacketHandler {
	private WorldRegisteredPacketBuilder builder = new WorldRegisteredPacketBuilder();

	public void handlePacket(Packet p, IoSession session) throws Exception {
		final long uID = ((LSPacket) p).getUID();
		builder.setUID(uID);
		builder.setSuccess(false);

		Server server = Server.getServer();
		if (((LSPacket) p).getID() == 1) {
			int id = p.readShort();
			if (server.getWorld(id) == null) {
				World world = server.getIdleWorld(id);
				if (world == null) {
					world = new World(id, session);
					if (!Server.devMode) {
						int passL = p.readInt();
						byte[] pass = p.readBytes(passL);
						if (!Arrays.equals(
								new Hash(Config.LS_CONNECT_PASS.getBytes())
										.value(), pass)) {
							System.out
									.println("World provided invalid password.");
							LSPacket temp = builder.getPacket();
							if (temp != null) {
								session.write(temp);
							}
							return;
						}
					} else {
						int length = p.readInt();
						if (length != 0) {
							System.out
									.println("[WARNING] Loginserver is in dev mode, but gameserver is not! Connection refused.");
							LSPacket temp = builder.getPacket();
							if (temp != null) {
								session.write(temp);
							}
							return;
						}						
					}
					server.registerWorld(world);
					System.out.println("Registering world: " + id);
				} else {
					world.setSession(session);
					server.setIdle(world, false);
					System.out.println("Reattached to world " + id);
				}
				int playerCount = p.readShort();
				for (int i = 0; i < playerCount; i++) {
					world.registerPlayer(p.readLong(),
							DataConversions.IPToString(p.readLong()),
							p.readString(p.readInt()));
				}
				session.setAttachment(world);
				builder.setSuccess(true);
			}
		} else {
			World world = (World) session.getAttachment();

			server.unregisterWorld(world);
			System.out.println("UnRegistering world: " + world.getID());
			session.setAttachment(null);
			builder.setSuccess(true);
		}

		LSPacket temp = builder.getPacket();
		if (temp != null) {
			session.write(temp);
		}
	}
}
