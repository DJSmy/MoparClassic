package org.moparscape.msc.ls;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Collection;
import java.util.Scanner;
import java.util.TreeMap;

import org.apache.mina.common.IoAcceptor;
import org.apache.mina.common.IoAcceptorConfig;
import org.apache.mina.common.IoHandler;
import org.apache.mina.transport.socket.nio.SocketAcceptor;
import org.apache.mina.transport.socket.nio.SocketAcceptorConfig;
import org.apache.mina.transport.socket.nio.SocketSessionConfig;
import org.moparscape.msc.ls.model.PlayerSave;
import org.moparscape.msc.ls.model.World;
import org.moparscape.msc.ls.net.FConnectionHandler;
import org.moparscape.msc.ls.net.LSConnectionHandler;
import org.moparscape.msc.ls.packethandler.local.Command;
import org.moparscape.msc.ls.packethandler.local.CommandHandler;
import org.moparscape.msc.ls.persistence.StorageMedium;
import org.moparscape.msc.ls.persistence.impl.StorageMediumFactory;
import org.moparscape.msc.ls.reddit.RedditTasks;
import org.moparscape.msc.ls.util.Config;

public class Server {
	public static StorageMedium storage;
	private static Server server;
	public static boolean devMode = false;

	public static void error(Object o) {
		if (o instanceof Exception) {
			Exception e = (Exception) o;
			e.printStackTrace();
			System.exit(1);
			return;// Adding save data
		}
		System.err.println(o.toString());
	}

	public static Server getServer() {
		if (server == null) {
			server = new Server();
		}
		return server;
	}

	public static void main(String[] args) throws IOException {
		String configFile = "conf" + File.separator + "Config.xml";
		if (args.length > 0) {
			File f = new File(args[0]);
			if (f.exists()) {
				configFile = f.getName();
			} else {
				System.out.println("Config not found: " + f.getCanonicalPath());
				displayConfigDefaulting(configFile);
			}
		} else {
			System.out.println("No config file specified.");
			displayConfigDefaulting(configFile);
		}
		Config.initConfig(configFile);

		if (Config.LS_CONNECT_PASS == null || Config.LS_CONNECT_PASS.equals("")) {
			if (new File("conf", "DEVMODE").exists()) {
				devMode = true;
				System.out.println("[WARNING] Loginserver is in dev mode.");
			} else {
				System.out
						.println("You must specify a ls-connect-pass in the config, or make a file called DEVMODE in the config folder.");
				System.exit(0);
			}
		}

		System.out.println("Login Server starting up...");		
		try {
			storage = StorageMediumFactory.create(Config.STORAGE_MEDIUM);
		} catch (Exception e) {
			e.printStackTrace();
			return;
		}
		System.out.println("Storage Medium: "
				+ storage.getClass().getSimpleName());
		Server.getServer();
		if (Config.USE_REDDIT) {
			RedditTasks.start();
		}
		try (Scanner scan = new Scanner(System.in)) {
			CommandHandler handler = new CommandHandler();
			String command;
			while ((command = scan.nextLine()) != null) {
				handler.handle(new Command(command));
			}
		}
	}

	/**
	 * The login engine
	 */
	private LoginEngine engine;
	/**
	 * The Server SocketAcceptor
	 */
	private IoAcceptor frontendAcceptor;

	private TreeMap<Integer, World> idleWorlds = new TreeMap<Integer, World>();

	/**
	 * The Server SocketAcceptor
	 */
	private IoAcceptor serverAcceptor;

	private TreeMap<Integer, World> worlds = new TreeMap<Integer, World>();

	private Server() {
		try {
			engine = new LoginEngine(this);
			engine.start();
			serverAcceptor = createListener(Config.LS_IP, Config.LS_PORT,
					new LSConnectionHandler(engine));
			frontendAcceptor = createListener(Config.QUERY_IP,
					Config.QUERY_PORT, new FConnectionHandler(engine));
		} catch (IOException e) {
			Server.error(e);
		}
	}

	private IoAcceptor createListener(String ip, int port, IoHandler handler)
			throws IOException {
		final IoAcceptor acceptor = new SocketAcceptor();
		IoAcceptorConfig config = new SocketAcceptorConfig();
		config.setDisconnectOnUnbind(true);
		((SocketSessionConfig) config.getSessionConfig()).setReuseAddress(true);
		acceptor.bind(new InetSocketAddress(ip, port), handler, config);
		Runtime.getRuntime().addShutdownHook(new Thread() {
			@Override
			public void run() {
				acceptor.unbindAll();
			}
		});
		return acceptor;
	}

	public PlayerSave findSave(long user, World world) {
		PlayerSave save = world.getSave(user);
		if (save == null) {
			save = PlayerSave.loadPlayer(user);
		}
		world.assosiateSave(save);
		return save;
	}

	public World findWorld(long user) {
		for (World w : getWorlds()) {
			if (w.hasPlayer(user)) {
				return w;
			}
		}
		return null;
	}

	public LoginEngine getEngine() {
		return engine;
	}

	public World getIdleWorld(int id) {
		return idleWorlds.get(id);
	}

	public World getWorld(int id) {
		if (id < 0) {
			return null;
		}
		return worlds.get(id);
	}

	public Collection<World> getWorlds() {
		return worlds.values();
	}

	public boolean isRegistered(World world) {
		return getWorld(world.getID()) != null;
	}

	public void kill() {
		try {
			serverAcceptor.unbindAll();
			frontendAcceptor.unbindAll();
			storage.shutdown();
		} catch (Exception e) {
			Server.error(e);
		}
	}

	public boolean registerWorld(World world) {
		int id = world.getID();
		if (id < 0 || getWorld(id) != null) {
			return false;
		}
		worlds.put(id, world);
		return true;
	}

	public void setIdle(World world, boolean idle) {
		if (idle) {
			worlds.remove(world.getID());
			idleWorlds.put(world.getID(), world);
		} else {
			idleWorlds.remove(world.getID());
			worlds.put(world.getID(), world);
		}
	}

	public boolean unregisterWorld(World world) {
		int id = world.getID();
		if (id < 0) {
			return false;
		}
		if (getWorld(id) != null) {
			worlds.remove(id);
			return true;
		}
		if (getIdleWorld(id) != null) {
			idleWorlds.remove(id);
			return true;
		}
		return false;
	}

	private static void displayConfigDefaulting(String file) {
		System.out.println("Defaulting to use " + file);
	}
}