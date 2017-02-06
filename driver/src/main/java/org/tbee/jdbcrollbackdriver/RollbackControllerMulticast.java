/**
 * 
 */
package org.tbee.jdbcrollbackdriver;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.ref.Reference;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;

/**
 *
 */
public class RollbackControllerMulticast {
	final static org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(RollbackControllerMulticast.class);

	final private static String ROLLBACK = "ROLLBACK";
	final private static String ALLOWTRANSACTIONS = "allowTransactions";
	final private static String DISABLETRANSACTIONS = "disableTransactions";

	static void startListening() {
		if (!useMulticast()) {
			System.out.println(RollbackControllerMulticast.class.getSimpleName() + ": not using multicast");
			return;
		}
		
		serverThread = new Thread( () -> {
			Thread.currentThread().setName(RollbackControllerMulticast.class.getSimpleName() + "-server");
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

		String ip = getIP();
		int port = getPort();

		// create a server socket
	    if (logger.isInfoEnabled()) logger.info(RollbackControllerMulticast.class.getSimpleName() + ": listening to " + ip + ":" + port);
	    System.out.println(RollbackControllerMulticast.class.getSimpleName() + ": listening to " + ip + ":" + port);
		try (
			MulticastSocket socket = new MulticastSocket(port);
		){
			// join group
		    InetAddress group = InetAddress.getByName(ip);
			socket.joinGroup(group);

			// forever
			while (forever) {
				
				try {		
					DatagramPacket packet;
				    byte[] buf = new byte[256];
				    packet = new DatagramPacket(buf, buf.length);
				    if (logger.isDebugEnabled()) logger.debug(RollbackControllerMulticast.class.getSimpleName() + ": Listening on " + ip + ":" + port);
				    socket.receive(packet);
				    String received = new String(packet.getData(), 0, packet.getLength());
				    if (logger.isDebugEnabled()) logger.debug(RollbackControllerMulticast.class.getSimpleName() + ": Received : " + received);
				    
				    // react to the received packet
				    if (ROLLBACK.equals(received)) {
						rollbackController.rollbackAll();
				    }
				    else if (ALLOWTRANSACTIONS.equals(received)) {
						rollbackController.allowTransactions();
				    }
				    else if (DISABLETRANSACTIONS.equals(received)) {
						rollbackController.disableTransactions();
		    		}
				}
				catch (Exception e) {
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
	static private void broadcast(String msg) {
		String ip = getIP();
		int port = getPort();
		try (
			MulticastSocket socket = new MulticastSocket(port);
		){
			// join group
			InetAddress group = InetAddress.getByName(ip);
			socket.joinGroup(group);

			// send command
		    if (logger.isDebugEnabled()) logger.debug(RollbackControllerMulticast.class.getSimpleName() + ": Sending to " + ip + ":" + port + " " + msg);
			DatagramPacket sendPacket = new DatagramPacket(msg.getBytes(), msg.length(), group, 7777);
			socket.send(sendPacket);
		    if (logger.isDebugEnabled()) logger.debug(RollbackControllerMulticast.class.getSimpleName() + ": Send " + msg);
		}
		catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * 
	 */
	static String getIP() {
		String host = (System.getProperty(RollbackControllerMulticast.class.getSimpleName() + ".ip") == null ? "" : System.getProperty(RollbackControllerMulticast.class.getSimpleName() + ".ip"));
		return host; 
	}

	/**
	 * 
	 */
	static int getPort() {
		int port = (System.getProperty(RollbackControllerMulticast.class.getSimpleName() + ".port") == null ? 0 : Integer.parseInt(System.getProperty(RollbackControllerMulticast.class.getSimpleName() + ".port")));
		return port;
	}

	/**
	 * 
	 */
	static boolean useMulticast() {
		return getPort() > 0;
	}
	
	// ========================================================================================================================================================
	// API

	/**
	 * 
	 */
	static public void rollbackAll() {
		broadcast(ROLLBACK);
	}
	
	/**
	 * 
	 */
	static public void allowTransactions() {
		broadcast(ALLOWTRANSACTIONS);
	}
	
	/**
	 * 
	 */
	static public void disableTransactions() {
		broadcast(DISABLETRANSACTIONS);
	}
	
}
