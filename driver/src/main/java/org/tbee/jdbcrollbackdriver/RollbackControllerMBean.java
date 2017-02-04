/**
 * 
 */
package org.tbee.jdbcrollbackdriver;

/**
 * @author teugelink
 * @product CARESS
 * @project caress.javacore
 *
 */
public interface RollbackControllerMBean {
	
	void rollbackAll();  
	
	void allowTransactions();  
	
	void disableTransactions();  
}
