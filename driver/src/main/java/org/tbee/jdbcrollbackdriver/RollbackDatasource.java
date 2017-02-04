package org.tbee.jdbcrollbackdriver;

import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.logging.Logger;

import javax.sql.DataSource;

public class RollbackDatasource implements DataSource {

	private final String url;
	private final String username;
	private final String password;
	private final Connection connection;
	
	public RollbackDatasource(String url, String username, String password) throws SQLException {
		this.url = url;
		this.username = username;
		this.password = password;
		connection = DriverManager.getConnection("jdbc:rollbackonly:net.sourceforge.jtds.jdbc.Driver:jtds:sqlserver://CARESS-TA-DB04:1433;instance=SQL2014;sendStringParametersAsUnicode=false;databaseName=CRS_TST", "CRSADMIN", "CRSADMIN");		
	}

	@Override
	public PrintWriter getLogWriter() throws SQLException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void setLogWriter(PrintWriter out) throws SQLException {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void setLoginTimeout(int seconds) throws SQLException {
		// TODO Auto-generated method stub
		
	}

	@Override
	public int getLoginTimeout() throws SQLException {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public Logger getParentLogger() throws SQLFeatureNotSupportedException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public <T> T unwrap(Class<T> iface) throws SQLException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public boolean isWrapperFor(Class<?> iface) throws SQLException {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public Connection getConnection() throws SQLException {
		return connection;
	}

	@Override
	public Connection getConnection(String username, String password) throws SQLException {
		return connection;
	}

}
