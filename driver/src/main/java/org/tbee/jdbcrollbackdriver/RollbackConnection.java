package org.tbee.jdbcrollbackdriver;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;

public class RollbackConnection implements InvocationHandler {
	final static org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(RollbackDriver.class);

	static public java.sql.Connection wrap(RollbackDriver rollbackDriver, java.sql.Connection connection, String label) throws SQLException
	{
		// we are the handler
		RollbackConnection lThis = new RollbackConnection(rollbackDriver, connection, label);
		java.sql.Connection lConnection = (java.sql.Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class[] { java.sql.Connection.class }, lThis);
		return lConnection;
	}

	// ================================================================================================
	// Constructor
	
	public RollbackConnection(RollbackDriver rollbackDriver, Connection connection, String label) throws SQLException {
		this.rollbackDriver = rollbackDriver;
		this.connection = connection;
		this.label = label;
		
		// initialize
		connection.setAutoCommit(false);
		
		// read through locks
		connection.createStatement().execute("SET TRANSACTION ISOLATION LEVEL READ UNCOMMITTED"); // SQLServer specific TBEERNOT
	}
	final private RollbackDriver rollbackDriver;
	final private Connection connection;
	final private String label;
	
	// ================================================================================================
	// InvocationHandler
	// http://www.practicalsqldba.com/2012/05/ms-sql-server-nested-transaction-and.html
	
	@Override
	public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
		if ("setAutoCommit".equals(method.getName())) {
			if (rollbackDriver.getTransactionsEnabled()) {
				if (logger.isTraceEnabled()) logger.trace(label + " allowing " + method.getName() + "(" + args[0] + ")");		
			}
			else {
				if (logger.isTraceEnabled()) logger.trace(label + " blocking " + method.getName() + "(" + args[0] + ")");
				return null;
			}
		}
		if ("commit".equals(method.getName())) {
			if (rollbackDriver.getTransactionsEnabled()) {
				if (logger.isDebugEnabled()) logger.debug(label + " allowing " + method.getName());		
			}
			else {
				if (logger.isDebugEnabled()) logger.debug(label + " blocking " + method.getName());
				return null;
			}
		}
		if ("rollback".equals(method.getName())) {
			if (rollbackDriver.getTransactionsEnabled()) {
				if (logger.isDebugEnabled()) logger.debug(label + " allowing " + method.getName());
			}
			else {
				if (logger.isDebugEnabled()) logger.debug(label + " blocking " + method.getName());
				return null;
			}
		}
		if ("close".equals(method.getName())) {
			if (logger.isDebugEnabled()) logger.debug(label + " blocking " + method.getName());
			return null;
		}

		Object result = method.invoke(connection, args);
		
		if ("commit".equals(method.getName())) {
			if (logger.isDebugEnabled()) logger.debug(label + " SET ISOLATION READ UNCOMMITTED");
			connection.createStatement().execute("SET TRANSACTION ISOLATION LEVEL READ UNCOMMITTED"); // SQLServer specific
		}
		if ("rollback".equals(method.getName())) {
			if (logger.isDebugEnabled()) logger.debug(label + " SET ISOLATION READ UNCOMMITTED");
			connection.createStatement().execute("SET TRANSACTION ISOLATION LEVEL READ UNCOMMITTED"); // SQLServer specific
		}
		return result;
	}
}
