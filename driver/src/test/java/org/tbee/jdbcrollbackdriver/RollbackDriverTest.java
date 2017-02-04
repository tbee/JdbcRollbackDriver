package org.tbee.jdbcrollbackdriver;

import org.junit.Test;

/**
 */
public class RollbackDriverTest {
	
	@Test
	public void loadingTheDriverShouldWork() throws ClassNotFoundException {
		Class.forName("org.tbee.jdbcrollbackdriver.RollbackDriver");
		// TODO: open a test driver URL
	}
}
