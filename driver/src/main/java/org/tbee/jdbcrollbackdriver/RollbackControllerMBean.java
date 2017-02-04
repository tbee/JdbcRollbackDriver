/**
 * 
 */
package org.tbee.jdbcrollbackdriver;

/**
 *
 */
public interface RollbackControllerMBean {
	
	void rollbackAll();  
	
	void allowTransactions();  
	
	void disableTransactions();  
}
