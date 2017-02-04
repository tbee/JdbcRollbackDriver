package org.tbee.jdbcrollbackdriver;

import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.Properties;

/**
 * This driver is intended to be used during integration and UI testing.
 * It can be put in disabled transaction mode, which will ignore any commit and rollback call.
 * At the end of the test the rollback call on the driver rolls back all changes on all connections.
 * 
 * One issue is the fact that mijnCaress uses multiple webapps, which will each create their own instance of the JDBC driver.
 * This driver will make sure that all connections of all drivers are rollbacked.
 * 
 * Each driver will  create one connection in order to minimize chances of locking conflicts.
 * Because certain processes (like logging in) requires a change to be written to the database, the driver can switch between allow or disable transactions.
 * For this use the corresponding static methods.
 * 
 *  Example:
 *  jdbc:rollback:theId#net.sourceforge.jtds.jdbc.Driver:jtds:sqlserver://CARESS-TA-DB04:1433;instance=SQL2012;sendStringParametersAsUnicode=false;databaseName=CRS_TST_EP
 */
public class RollbackDriver implements Driver {
	final static org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(RollbackDriver.class);

	// ================================================================================================
	// DriverManager

	static {
		register();
	}
	static private RollbackControllerMBean rollbackControllerMBean = null;

	/**
	 * Register the Driver with DriverManager
	 */
	static public void register() {
		// Register the Driver with DriverManager
		try {
			RollbackDriver driver = new RollbackDriver();
			if (logger.isDebugEnabled()) logger.debug("registering RollbackDriver with the DriverManager [" + Integer.toHexString(driver.hashCode()) + "]");
			DriverManager.registerDriver(driver);			
		} 
		catch (Exception e) {
			throw new RuntimeException("JDBC driver can't be registered", e);
		}
	}
	
	// ================================================================================================
	// Constructor
	
	public RollbackDriver() {
		// a classloader may create multiple instances of this driver, we need to rollback them all
		if (logger.isDebugEnabled()) logger.debug("Instantiated RollbackDriver [" + Integer.toHexString(hashCode()) + "]");
	}
	
	// ================================================================================================
	// Driver

	final static public String PREFIX = "jdbc:rollback:";

	@Override
	public Connection connect(String url, Properties info) throws SQLException {
		RollbackController.startControllingDriver(this, label);
		try {
			if (logger.isDebugEnabled()) logger.debug("Connect " + url);
	
			// check URL
			// do not use acceptsURL, because then inheriting this Driver will not work
			if (!url.startsWith(PREFIX)) {
				if (logger.isDebugEnabled()) logger.debug("Prefix does not match, this driver cannot connect: " + url);
				return null;
			}
	
			// extract parameters
			String driverPlusUrl = url.substring(PREFIX.length());
			String actualDriverClass = driverPlusUrl.substring(0, driverPlusUrl.indexOf(":"));
			actualUrl = "jdbc" + driverPlusUrl.substring(actualDriverClass.length());
			if (actualDriverClass.contains("#")) {
				label = actualDriverClass.substring(0, actualDriverClass.indexOf("#")) + ": ";
				actualDriverClass = actualDriverClass.substring(actualDriverClass.indexOf("#") + 1);
			}
		    if (logger.isDebugEnabled()) logger.debug(label + this.getClass().getSimpleName() + ": managing " + actualUrl);
			if (logger.isDebugEnabled()) logger.debug(label + "actualUrl = " + actualUrl);
			if (logger.isDebugEnabled()) logger.debug(label + "actualDriverClass = " + actualDriverClass);

			// now loading actual driver
			driver = (Driver)Class.forName(actualDriverClass).newInstance(); 
	
			// create connection
			if (wrappedConnection == null) {
				if (logger.isInfoEnabled()) logger.info(label + "creating connection: " + actualUrl + " in driver [" + Integer.toHexString(hashCode()) + "]");
				actualConnection = driver.connect(actualUrl, info);
		
				// wrap it
				wrappedConnection = RollbackConnection.wrap(this, actualConnection, label);
			}
			
			// done
			return wrappedConnection;
		}
		catch (ClassNotFoundException | InstantiationException | IllegalAccessException e) {
			throw new SQLException(e);
		}
	}
	private Driver driver;
	static private String actualUrl;
	static private String label = "";
	static private Connection actualConnection;
	static private Connection wrappedConnection;
	
	@Override
	public boolean acceptsURL(String url) throws SQLException {
		// must begin with correct string
		if (!url.startsWith(PREFIX)) {
			if (logger.isDebugEnabled()) logger.debug("Prefix does not match, this driver cannot connect: " + url);
			return false;
		} 
		else {
			if (logger.isDebugEnabled()) logger.debug("acceptsURL " + url);
		}
		
		// accept
		return true;
	}

	@Override
	public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) throws SQLException {
		return driver.getPropertyInfo(url, info);
	}

	@Override
	public int getMajorVersion() {
		return driver.getMajorVersion();
	}

	@Override
	public int getMinorVersion() {
		return driver.getMinorVersion();
	}

	@Override
	public boolean jdbcCompliant() {
		return driver.jdbcCompliant();
	}

	@Override
	public java.util.logging.Logger getParentLogger() throws SQLFeatureNotSupportedException {
		return driver.getParentLogger();
	}
	
	// ================================================================================================
	// Driver spanning API

	static public void rollbackAll() {  
		if (logger.isDebugEnabled()) logger.debug(label + "rollbackAll");
		if (usingSocket()) {
			RollbackControllerSocket.rollbackAll();
		}
		else if (usingMulticast()) {
			// rollbackMulticast.rollbackAllDrivers();
		}
		else {
			getControllerBean().rollbackAll();
		}
	}
	
	static public void allowTransactions() {  
		if (logger.isDebugEnabled()) logger.debug(label + "allowTransactions");
		if (usingSocket()) {
			RollbackControllerSocket.allowTransactions();
		}
		else if (usingMulticast()) {
			// rollbackMulticast.allowTransactions();
		}
		else {
			getControllerBean().allowTransactions();
		}
	}
	
	static public void disableTransactions() {  
		if (logger.isDebugEnabled()) logger.debug(label + "disableTransactions");
		if (usingSocket()) {
			RollbackControllerSocket.disableTransactions();
		}
		else if (usingMulticast()) {
			// rollbackMulticast.disableTransactions();
		}
		else {
			getControllerBean().disableTransactions();
		}
	}
	
	static private RollbackControllerMBean getControllerBean() {
		if (rollbackControllerMBean == null) {
			rollbackControllerMBean = RollbackController.connect();
		}
		return rollbackControllerMBean;
	}

	static private boolean usingSocket() {
		return System.getProperty(RollbackControllerSocket.class.getSimpleName()) != null;
	}

	static private boolean usingMulticast() {
		return System.getProperty(RollbackControllerMulticast.class.getSimpleName()) != null;
	}
	
	// ================================================================================================
	// Actual implementation of the API's actions (after the message has been received)

	public void rollback() {
		try {
			if (logger.isDebugEnabled()) logger.debug(label + "rollback on RollbackDriver [" + Integer.toHexString(hashCode()) + "] " + actualConnection);
			if (actualConnection != null) {
				setAutoCommitFalse();
				System.out.println(label + "rolling back " + actualUrl);
				actualConnection.rollback();
			}
		}
		catch (SQLException e) {
			throw new RuntimeException(e);
		}
	}
	
	public boolean getTransactionsEnabled() {
		//if (logger.isDebugEnabled()) logger.debug(label + "getTransactionsEnabled=" + transactionsEnabled + " [" + Integer.toHexString(hashCode()) + "]");
		return transactionsEnabled;
	}
	public void setTransactionsEnabled(boolean v) {
		if (logger.isDebugEnabled()) logger.debug(label + "setTransactionsEnabled(" + v + ") [" + Integer.toHexString(hashCode()) + "]");
		transactionsEnabled = v;
		if (v == false) {
			setAutoCommitFalse();
		}
	}

	private void setAutoCommitFalse() {
		try {
			if (actualConnection != null) {
				actualConnection.setAutoCommit(false);
			}
		} 
		catch (SQLException e) {
			throw new RuntimeException(e);
		}
	}
	private boolean transactionsEnabled = true;
}
