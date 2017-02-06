/**
 * 
 */
package org.tbee.jdbcrollbackdriver;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;

/**
 *
 */
public class RollbackControllerSocket {
	final static org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(RollbackControllerSocket.class);

	private static final String ROLLBACKAll_ACTION = "rollbackAll";
	private static final String ALLOWTRANSACTIONS_ACTION = "allowTransactions";
	private static final String DISABLETRANSACTIONS_ACTION = "disableTransactions";

	/**
	 * 
	 */
	static void startListening() {
		// are we using sockets or not?
		if (!useSocket()) {
			System.out.println(RollbackControllerSocket.class.getSimpleName() + ": not using socket");
			return;
		}
		
		// start listening for commands coming in
		serverThread = new Thread( () -> {
			Thread.currentThread().setName(RollbackControllerSocket.class.getSimpleName() + "-server");
			waitForData();	
		});
		serverThread.start();
	}
	static Thread serverThread;

	/**
	 * Stop the listener
	 */
	public static void stop() {
		forever = false;
		serverThread.stop(); // TBEERNOT we can do better
	}
	
	/**
	 * 
	 */
	private static void waitForData() {
		
		// connect to the MBean locally (bypassing the VPN problems)
		RollbackControllerMBean rollbackController = RollbackController.connectLocally();
		
		// create a server socket
		try (
			ServerSocket serverSocket = new ServerSocket(getPort());
		){
			
			// forever
			while (forever) {
				
				// wait for some instruction coming in
				System.out.println(RollbackControllerSocket.class.getSimpleName() + "-server waiting for client on localhost:" + serverSocket.getLocalPort() + "...");
				if (logger.isDebugEnabled()) logger.debug("Waiting for client on port " + serverSocket.getLocalPort() + "...");
				try (
					Socket server = serverSocket.accept();
					DataInputStream in = new DataInputStream(server.getInputStream());
				){
					
					// get the instruction
					String type = in.readUTF();
					if (logger.isDebugEnabled()) logger.debug("Connection coming in from " + server.getRemoteSocketAddress() + ", processing command: " + type);
					System.out.println(RollbackControllerSocket.class.getSimpleName() + "-server connection coming in from " + server.getRemoteSocketAddress() + ", processing command: " + type);
					
					// process instruction
					if (ROLLBACKAll_ACTION.equals(type)) {
						rollbackController.rollbackAll();
					}
					else if (ALLOWTRANSACTIONS_ACTION.equals(type)) {
						rollbackController.allowTransactions();
					}
					else if (DISABLETRANSACTIONS_ACTION.equals(type)) {
						rollbackController.disableTransactions();
					}
				} 
				catch (IOException e) {
					e.printStackTrace();
					return; // stop the server
				}
			}
		} 
	    catch (IOException e) {
			e.printStackTrace();
		}
	}
	static boolean forever = true;

	/**
	 * 
	 */
	private static void send(String s) {
		String serverName = getHost();
		int port = getPort();

		// connect
		if (logger.isDebugEnabled()) logger.debug("Connecting to " + serverName + " on port " + port);
		try (
			Socket client = new Socket(serverName, port);
			DataOutputStream out = new DataOutputStream( client.getOutputStream() );
		){
			// send
			System.out.println(RollbackControllerSocket.class.getSimpleName() + "-client connected to " + client.getRemoteSocketAddress() + ", sending " + s);
			if (logger.isDebugEnabled()) logger.debug("Connected to " + client.getRemoteSocketAddress() + ", sending " + s);
			out.writeUTF(s);
		} 
		catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * 
	 */
	static String getHost() {
		String host = (System.getProperty(RollbackControllerSocket.class.getSimpleName() + ".host") == null ? "localhost" : System.getProperty(RollbackControllerSocket.class.getSimpleName() + ".host"));
		return host; 
	}

	/**
	 * 
	 */
	static int getPort() {
		int port = (System.getProperty(RollbackControllerSocket.class.getSimpleName() + ".port") == null ? 0 : Integer.parseInt(System.getProperty(RollbackControllerSocket.class.getSimpleName() + ".port")));
		return port;
	}

	/**
	 * 
	 */
	static boolean useSocket() {
		return getPort() > 0;
	}
	
	// ========================================================================================================================================================
	// API
	
	/** 
	 * 
	 */
	static public void rollbackAll() {
        send(ROLLBACKAll_ACTION);
	}

	/** 
	 * 
	 */
	static public void allowTransactions() {
        send(ALLOWTRANSACTIONS_ACTION);
	}

	/** 
	 * 
	 */
	static public void disableTransactions() {
        send(DISABLETRANSACTIONS_ACTION);
	}
}
