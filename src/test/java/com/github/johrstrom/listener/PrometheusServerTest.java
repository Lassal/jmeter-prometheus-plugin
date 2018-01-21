package com.github.johrstrom.listener;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;

import org.junit.Before;
import org.junit.Test;


public class PrometheusServerTest {
	
	private PrometheusServer server = PrometheusServer.getInstance();
	
	@Before
	public void setup() {
		
	}
	
	@Test
	public void ensureCleanStartStop() throws Exception {
		server.start();
		server.stop();
	}
	
	


}
