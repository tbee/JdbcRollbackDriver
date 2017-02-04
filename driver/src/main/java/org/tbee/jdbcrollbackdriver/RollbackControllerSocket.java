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

	static void startListening() {
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
	 * @return
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
				System.out.println(RollbackControllerSocket.class.getSimpleName() + "-server waiting for client on port " + serverSocket.getLocalPort() + "...");
				if (logger.isDebugEnabled()) logger.debug("Waiting for client on port " + serverSocket.getLocalPort() + "...");
				try (
					Socket server = serverSocket.accept();
				){
					
					// get the instruction
					if (logger.isDebugEnabled()) logger.debug("Connection coming in from " + server.getRemoteSocketAddress());
					DataInputStream in = new DataInputStream(server.getInputStream());
					String type = in.readUTF();
					if (logger.isDebugEnabled()) logger.debug("Processing command: " + type);
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
		try {
			// connect
			if (logger.isDebugEnabled()) logger.debug("Connecting to " + serverName + " on port " + port);
			Socket client = new Socket(serverName, port);

			// send
			System.out.println(RollbackControllerSocket.class.getSimpleName() + "-client connected to " + client.getRemoteSocketAddress() + ", sending " + s);
			if (logger.isDebugEnabled()) logger.debug("Connected to " + client.getRemoteSocketAddress() + ", sending " + s);
			OutputStream outToServer = client.getOutputStream();
			DataOutputStream out = new DataOutputStream(outToServer);
			out.writeUTF(s);
			
			// and forget
			client.close();
		} 
		catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	static private String getHost() {
		String host = (System.getProperty(RollbackControllerSocket.class.getSimpleName() + ".host") == null ? "localhost" : System.getProperty("RollbackControllerMBean.host"));
		return host; 
	}
	
	static private int getPort() {
		int port = (System.getProperty(RollbackControllerSocket.class.getSimpleName() + ".port") == null ? DEFAULT_PORT : Integer.parseInt(System.getProperty("RollbackControllerMBean.port")));
		return port;
	}
	static int DEFAULT_PORT = 3333;
	
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
