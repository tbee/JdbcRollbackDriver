/**
 * 
 */
package org.tbee.jdbcrollbackdriver;

import java.io.IOException;
import java.lang.management.ManagementFactory;

import javax.management.InstanceAlreadyExistsException;
import javax.management.InstanceNotFoundException;
import javax.management.MBeanException;
import javax.management.MBeanServer;
import javax.management.MBeanServerConnection;
import javax.management.MBeanServerInvocationHandler;
import javax.management.MalformedObjectNameException;
import javax.management.NotCompliantMBeanException;
import javax.management.Notification;
import javax.management.NotificationBroadcasterSupport;
import javax.management.NotificationListener;
import javax.management.ObjectName;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;

/**
 * This class uses MBeans to communicate state changes between RollbackDrivers.
 * By using MBean it syncs multiple instances (e.g. because of different classloaders like in webapps).
 * Usage is simple:
 * - The RollbackDriver must register the MBean (registerMBean).
 * - It then must register itself to the notifications the MBean receives, in order to change it state (startControllingDriver).
 * - The tests can connect to this MBean and issue its methods (connect).
 */
public class RollbackController extends NotificationBroadcasterSupport
implements RollbackControllerMBean {

	final static org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(RollbackController.class);
	
	static final String MBEAN_NAME = RollbackControllerMBean.class.getPackage().getName() + ":type=" + RollbackControllerMBean.class.getSimpleName();
	
	private static final String ROLLBACKAll_ACTION = "rollbackAll";
	private static final String ALLOWTRANSACTIONS_ACTION = "allowTransactions";
	private static final String DISABLETRANSACTIONS_ACTION = "disableTransactions";
	
	// ========================================================================================================================================================
	// MBean implementation
	
	/** 
	 * Invoke this MBean method to cause all RollbackDrivers to rollback
	 */
	@Override
	public void rollbackAll() {
		if (logger.isDebugEnabled()) logger.debug("rollbackAll");
        sendNotification(new Notification(ROLLBACKAll_ACTION, this, sequenceNumber++));
	}

	/** 
	 * Invoke this MBean method to cause all RollbackDrivers to allow commits
	 */
	@Override
	public void allowTransactions() {
		if (logger.isDebugEnabled()) logger.debug("allowTransactions");
        sendNotification(new Notification(ALLOWTRANSACTIONS_ACTION, this, sequenceNumber++));
	}

	/** 
	 * Invoke this MBean method to cause all RollbackDrivers to ignore commits
	 */
	@Override
	public void disableTransactions() {
		if (logger.isDebugEnabled()) logger.debug("disableTransactions");
        sendNotification(new Notification(DISABLETRANSACTIONS_ACTION, this, sequenceNumber++));
	}
	
	private long sequenceNumber = 1;

	// ========================================================================================================================================================
	// Supporting methods wrapping the MBean implementation away from the Driver and test runner
	
	/**
	 * 
	 */
	private static void registerMBean() {
		if (logger.isDebugEnabled()) logger.debug("Check to see if the MBean needs to be registed");
		try {
			// Register the MBean if it is not registered yet
			MBeanServer mbeanServer = ManagementFactory.getPlatformMBeanServer();
	        ObjectName objectName = new ObjectName(MBEAN_NAME);
			if (!mbeanServer.isRegistered(objectName)) {
				RollbackController mbean = new RollbackController();
				if (logger.isInfoEnabled()) logger.info("Registering the MBean " + MBEAN_NAME);
//				Logger.getLogger("javax.management.mbeanserver").setLevel(Level.FINEST);
				mbeanServer.registerMBean(mbean, objectName);
				
				// start listening for the socket as well
				RollbackControllerSocket.startListening();
			}
		} 
		catch (MalformedObjectNameException | InstanceAlreadyExistsException | NotCompliantMBeanException | MBeanException e) {
			throw new RuntimeException(e);
		}		
	}
	
	/**
	 * Make the driver change its state when the controller is told to do so 
	 */
	public static void startControllingDriver(final RollbackDriver rollbackDriver, final String label) {
		registerMBean();

		if (logger.isInfoEnabled()) logger.info(label + "Taking control over the JDBC driver");
		try {
			MBeanServer mbeanServer = ManagementFactory.getPlatformMBeanServer();
			ObjectName objectName = new ObjectName(MBEAN_NAME);
			mbeanServer.addNotificationListener(objectName, new NotificationListener() {
				@Override
				public void handleNotification(Notification notification, Object handback) {
					
					// depending on the type of the notification, make the drive change state
					String type = notification.getType();
					if (logger.isDebugEnabled()) logger.debug((label == null ? "" : label) + "Notification for coming in: " + type);
					if (ROLLBACKAll_ACTION.equals(type)) {
						rollbackDriver.rollback();
					}
					if (ALLOWTRANSACTIONS_ACTION.equals(type)) {
						rollbackDriver.setTransactionsEnabled(true);
					}
					if (DISABLETRANSACTIONS_ACTION.equals(type)) {
						rollbackDriver.setTransactionsEnabled(false);
					}
				}
			}, null, null);
		} 
		catch (InstanceNotFoundException | MalformedObjectNameException e) {
			throw new RuntimeException(e);
		}		
	}
	
	/**
	 * Connect to the MBean from another JVM (e.g. the JVM running the Cucumber or integration tests)
	 * Use -DRollbackControllerMBean.host=localhost to specify the host 
	 * Use -DRollbackControllerMBean.port=7676 to specify the port 
	 */
	public static RollbackControllerMBean connect() {
		String host = (System.getProperty("RollbackControllerMBean.host") == null ? "localhost" : System.getProperty("RollbackControllerMBean.host"));
		if (host.contains(":")) {
			host = "[" + host + "]"; // IPv6 requires brackets around the URL
		}
		int port = (System.getProperty("RollbackControllerMBean.port") == null ? 7676 : Integer.parseInt(System.getProperty("RollbackControllerMBean.port"))); 
		String url = "service:jmx:rmi:///jndi/rmi://" + host + ":" + port + "/jmxrmi";
		System.out.println("Connecting to RollbackControllerMBean running on " + url);
		try {
			JMXServiceURL serviceUrl = new JMXServiceURL(url);
			JMXConnector jmxConnector = JMXConnectorFactory.connect(serviceUrl, null);
			MBeanServerConnection mbeanConn = jmxConnector.getMBeanServerConnection();
			ObjectName name = new ObjectName(MBEAN_NAME);
			RollbackControllerMBean mbean = MBeanServerInvocationHandler.newProxyInstance(mbeanConn, name, RollbackControllerMBean.class, false);
			return mbean;
		} 
		catch (IOException | MalformedObjectNameException e) {
			throw new RuntimeException("Error connecting to " + url, e);
		}
	}
	
	/**
	 * Connect to the MBean from inside this JVM 
	 */
	public static RollbackControllerMBean connectLocally() {
		System.out.println("Connecting to RollbackControllerMBean on this JVM");
		try {
			MBeanServer mbeanServer = ManagementFactory.getPlatformMBeanServer(); 
			ObjectName name = new ObjectName(MBEAN_NAME);
			RollbackControllerMBean mbean = MBeanServerInvocationHandler.newProxyInstance(mbeanServer, name, RollbackControllerMBean.class, false);
			return mbean;
		} 
		catch (MalformedObjectNameException e) {
			throw new RuntimeException("Error connecting local mbean", e);
		}
	}
}
