package org.tbee.jdbcrollbackdriver;

import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.Properties;

/**
 */
public class TestDriver implements Driver {


	/**
	 * Register the Driver with DriverManager
	 */
	static void register() {
		
		// Register the Driver with DriverManager
		try {
			RollbackDriver driver = new RollbackDriver();
			DriverManager.registerDriver(driver);			
		} 
		catch (Exception e) {
			throw new RuntimeException("JDBC driver can't be registered", e);
		}
	}
	

	final static public String PREFIX = "jdbc:test:";

	@Override
	public Connection connect(String url, Properties info) throws SQLException {
	
		// check URL
		// do not use acceptsURL, because then inheriting this Driver will not work
		if (!url.startsWith(PREFIX)) {
			return null;
		}

		return new TestConnection();
	}
	
	@Override
	public boolean acceptsURL(String url) throws SQLException {
		return url.startsWith(PREFIX);
	}

	@Override
	public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) throws SQLException {
		return null;
	}

	@Override
	public int getMajorVersion() {
		return 1;
	}

	@Override
	public int getMinorVersion() {
		return 0;
	}

	@Override
	public boolean jdbcCompliant() {
		return true;
	}

	@Override
	public java.util.logging.Logger getParentLogger() throws SQLFeatureNotSupportedException {
		return null;
	}
}
