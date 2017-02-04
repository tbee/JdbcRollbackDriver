package org.tbee.jdbcrollbackdriver;

import java.lang.management.ManagementFactory;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.sql.DriverManager;
import java.sql.SQLException;

import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;

import org.junit.Assert;
import org.junit.Test;

/**
 */
public class RollbackDriverTest {
	
	@Test
	public void loadingDriver() throws ClassNotFoundException, SQLException, MalformedObjectNameException {
		
		// GIVEN the driver is not loaded
		String url = "jdbc:rollback:TestId#org.tbee.jdbcrollbackdriver.TestDriver:test:blablabla";
		try {
			DriverManager.getConnection(url);
		}
		catch (SQLException e) {
			Assert.assertTrue(e.getMessage().contains("No suitable driver found"));
		}
				
		// THEN there should be no MBean server
		Assert.assertFalse(checkMBean());
		// AND there should be no open port
		Assert.assertFalse(checkIfPortIsUsed(RollbackControllerSocket.DEFAULT_PORT));
		
		// WHEN loading the driver
		Class.forName("org.tbee.jdbcrollbackdriver.RollbackDriver");
		
		// THEN there still should not be a mbean server (because this may be a client)
		Assert.assertFalse(checkMBean());
		// AND there should be no open port
		Assert.assertFalse(checkIfPortIsUsed(RollbackControllerSocket.DEFAULT_PORT));
		
		// WHEN the first connection is made
		DriverManager.getConnection(url);
		
		// THEN the mbean server should be active
		Assert.assertTrue(checkMBean());
		// AND there should be an open port
		Assert.assertTrue(checkIfPortIsUsed(RollbackControllerSocket.DEFAULT_PORT));
	}
	
	boolean checkMBean() throws MalformedObjectNameException {
		MBeanServer mbeanServer = ManagementFactory.getPlatformMBeanServer(); 
		ObjectName name = new ObjectName(RollbackController.MBEAN_NAME);
		return mbeanServer.queryNames(name, null).size() != 0;
	}
	
	boolean checkIfPortIsUsed(int port) {
		try {
			Socket socket = new Socket();
			socket.connect(new InetSocketAddress("127.0.0.1", port), 500);
			socket.close();
			return true;
		} 
		catch (Exception ex) {
			return false;
		}
	}
}
