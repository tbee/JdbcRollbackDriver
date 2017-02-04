package org.tbee.jdbcrollbackdriver;

import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

/**
 * This class uses UDP multicast sockets to communicate between the driver instances
 * https://docs.oracle.com/javase/7/docs/api/java/net/MulticastSocket.html
 *
 * The preferred way is to use a cross-classloader singleton (using Thread.getContextClassloader), but that did not work.
 * Because the API is on the driver class, this communication mechanism can easily be replaced.
 */
public class RollbackControllerMulticast implements Runnable {
	final static org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(RollbackDriver.class);

	private MulticastSocket socket;
	
	// ================================================================================================
	// Constructor

	public RollbackControllerMulticast() {
		connect();
		new Thread(this).start();
	}
	
	// ================================================================================================
	// Drivers

	public void addDriver(RollbackDriver rollbackDriver, String label) {
	    if (logger.isDebugEnabled()) logger.debug(RollbackControllerMulticast.class.getSimpleName() + ": managing RollbackDriver[" + Integer.toHexString(rollbackDriver.hashCode()) + "]");
		rollbackDrivers.add(new WeakReference<RollbackDriver>(rollbackDriver));
		this.label = label;
	}
	final static private List<Reference<RollbackDriver>> rollbackDrivers = new ArrayList<>();
	private String label;
	
	// ================================================================================================
	// Listener

	private String IP_ADDRESS = "228.5.6.7";
	private int PORT = 7777;

	private void connect() {
		// default, configurable using -DRollbackMulticast-ip=1.2.3.4 or derive from the current host's IP address
		String ipString = System.getProperty(this.getClass().getSimpleName()+"-ip");
		if (ipString != null && ipString.trim().length() > 0) {
			IP_ADDRESS = ipString;
		}
		else {
			ipString = derriveIPStringFromNetworkInterfaces();
			if (ipString != null && ipString.trim().length() > 0) {
				IP_ADDRESS = ipString;
			}
		}

		// defalt or configurable using -DRollbackMulticast-port=1234
		String portString = System.getProperty(this.getClass().getSimpleName()+"-port");
		if (portString != null && portString.trim().length() > 0) {
			PORT = Integer.parseInt(portString);
		}
		
		// connect to the multicast address
		try {
		    if (logger.isInfoEnabled()) logger.info(label + this.getClass().getSimpleName() + ": listening to " + IP_ADDRESS + ":" + PORT + " [" + Integer.toHexString(hashCode()) + "]");
		    System.out.println(this.getClass().getSimpleName() + ": listening to " + IP_ADDRESS + ":" + PORT);
		    InetAddress group = InetAddress.getByName(IP_ADDRESS);
		    socket = new MulticastSocket(PORT);
			socket.joinGroup(group);
		}
		catch (Exception e) {
			e.printStackTrace();
			return;
		}
	}
	
	private String derriveIPStringFromNetworkInterfaces() {
		try {
			Enumeration<NetworkInterface> networkInterfaceEnumeration = NetworkInterface.getNetworkInterfaces();
			while(networkInterfaceEnumeration.hasMoreElements())
			{
			    Enumeration<InetAddress> inetAddressEnumeration = networkInterfaceEnumeration.nextElement().getInetAddresses();
			    while (inetAddressEnumeration.hasMoreElements())
			    {
			        String ip = inetAddressEnumeration.nextElement().getHostAddress();
			        if (ip.contains(".") && !ip.startsWith("127.")) { // IPv4 (http://www.iana.org/assignments/ipv6-multicast-addresses/ipv6-multicast-addresses.xhtml#ipv6-scope)
			        	// Addresses in the range 224.xxx.xxx.xxx through 239.xxx.xxx.xxx are multicast addresses.
			        	ip = "228" + ip.substring(ip.indexOf("."));
			        	return ip;
			        }
			    }
			}
			return null;
		}
		catch (SocketException e) {
			throw new RuntimeException(e);
		}
	}
	
	public void run() {
		Thread.currentThread().setName(this.getClass().getSimpleName() + "-" + Integer.toHexString(hashCode()));
		while (true) {
		
			try {		
				DatagramPacket packet;
			    byte[] buf = new byte[256];
			    packet = new DatagramPacket(buf, buf.length);
			    if (logger.isDebugEnabled()) logger.debug(label + this.getClass().getSimpleName() + ": Listening on " + IP_ADDRESS + ":" + PORT + " (" + Integer.toHexString(hashCode()) + ")");
			    socket.receive(packet);
			    String received = new String(packet.getData(), 0, packet.getLength());
			    if (logger.isDebugEnabled()) logger.debug(label + this.getClass().getSimpleName() + ": Received (" + Integer.toHexString(hashCode()) + "): " + received);
			    
			    // react to the received packet
		    	for (Reference<RollbackDriver> rollbackDriverReference : new ArrayList<>(rollbackDrivers)) {
		    		RollbackDriver rollbackDriver = rollbackDriverReference.get();
		    		if (rollbackDriver == null) {
		    			rollbackDrivers.remove(rollbackDriverReference);
		    			continue;
		    		}
				    if (ROLLBACK.equals(received)) {
				    	rollbackDriver.rollback();
				    }
				    else if (ALLOWTRANSACTIONS.equals(received)) {
		    			rollbackDriver.setTransactionsEnabled(true);
				    }
				    else if (DISABLETRANSACTIONS.equals(received)) {
		    			rollbackDriver.setTransactionsEnabled(false);
		    		}
		    	}
			}
			catch (Exception e) {
				e.printStackTrace();
			}
		}
	}
	
	// ================================================================================================
	// Actions

	final private static String ROLLBACK = "ROLLBACK";
	final private static String ALLOWTRANSACTIONS = "allowTransactions";
	final private static String DISABLETRANSACTIONS = "disableTransactions";
	
	public void rollbackAllDrivers() {
		broadcast(ROLLBACK);
	}
	
	public void allowTransactions() {
		broadcast(ALLOWTRANSACTIONS);
	}
	
	public void disableTransactions() {
		broadcast(DISABLETRANSACTIONS);
	}
	
	private void broadcast(String msg) {
		try {
		    InetAddress group = InetAddress.getByName(IP_ADDRESS);

		    if (logger.isDebugEnabled()) logger.debug(label + this.getClass().getSimpleName() + ": Sending to " + IP_ADDRESS + ":" + PORT + " " + msg + " (" + Integer.toHexString(hashCode()) + ")");
			DatagramPacket sendPacket = new DatagramPacket(msg.getBytes(), msg.length(), group, 7777);
			socket.send(sendPacket);
		    if (logger.isDebugEnabled()) logger.debug(label + this.getClass().getSimpleName() + ": Send " + msg + " (" + Integer.toHexString(hashCode()) + ")");
		}
		catch (Exception e) {
			e.printStackTrace();
		}
	}
}
