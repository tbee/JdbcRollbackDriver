package org.tbee.jdbcrollbackdriver;

import java.lang.management.ManagementFactory;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.sql.DriverManager;
import java.sql.SQLException;

import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.servlet.ServletException;

import org.apache.catalina.LifecycleException;
import org.apache.catalina.core.StandardContext;
import org.apache.catalina.startup.Tomcat;
import org.apache.tomcat.JarScanFilter;
import org.apache.tomcat.JarScanType;
import org.junit.Assert;
import org.junit.Test;

/**
 */
public class RollbackDriverTest {
	

	@Test
	public void loadingDriver() throws ClassNotFoundException, SQLException, MalformedObjectNameException {
		String url = "jdbc:rollback:TestId#org.tbee.jdbcrollbackdriver.TestDriver:test:blablabla";
		
		// GIVEN the driver is not loaded
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
	
	@Test
	public void multipleInstances() throws ServletException, LifecycleException {
        Tomcat tomcat = new Tomcat();
        tomcat.setPort(8888);
        tomcat.enableNaming();
        JarScanFilter scanNothingJarScanFilter = new JarScanFilter() {
			@Override
			public boolean check(JarScanType jarScanType, String jarName) {
				return false; // scan nothing
			}
		};

		{
	        StandardContext standardContext = (StandardContext)tomcat.addContext("/webapp1", this.getClass().getResource(".").getFile());
	        standardContext.getNamingResources().addResource(createJdbcResource("DS1"));
	        standardContext.addApplicationListener( TestServletContextListener.class.getName() );
		}

		{
	        StandardContext standardContext = (StandardContext)tomcat.addContext("/webapp2", this.getClass().getResource(".").getFile());
	        standardContext.getNamingResources().addResource(createJdbcResource("DS2"));
	        standardContext.addApplicationListener( TestServletContextListener.class.getName() );
		}
		
        tomcat.start();
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

	private org.apache.tomcat.util.descriptor.web.ContextResource createJdbcResource(String name) {
		String url = "jdbc:rollback:" + name + "#org.tbee.jdbcrollbackdriver.TestDriver:test:blablabla";

		org.apache.tomcat.util.descriptor.web.ContextResource resource = new org.apache.tomcat.util.descriptor.web.ContextResource();
		resource.setAuth("Container");
		resource.setName("TestDS");
		resource.setType("javax.sql.DataSource");
		resource.setProperty("factory","org.apache.commons.dbcp2.BasicDataSourceFactory");
		resource.setProperty("driverClassName", "org.tbee.jdbcrollbackdriver.RollbackDriver");
		resource.setProperty("url", url);
		resource.setProperty("username", "u");
		resource.setProperty("password", "p");
        resource.setProperty("global", "jdbc/TestDS");
//		resource.setProperty("maxActive", "4");
		resource.setProperty("maxTotal", "8");
		resource.setProperty("maxIdle", "30");
		resource.setProperty("maxWaitMillis", "10000");
		resource.setProperty("removeAbandonedTimeout", "7200");
		resource.setProperty("removeAbandonedOnBorrow", "true");
		resource.setProperty("logAbandoned", "false");
		resource.setProperty("testOnBorrow", "false");
		resource.setProperty("testWhileIdle", "true");
		resource.setProperty("validationQuery", "SELECT 1");
		resource.setProperty("validationQueryTimeout", "15");
		resource.setProperty("timeBetweenEvictionRunsMillis", "5000");
		return resource;
	}
}
