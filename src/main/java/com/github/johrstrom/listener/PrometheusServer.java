package com.github.johrstrom.listener;

import org.apache.jmeter.util.JMeterUtils;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.eclipse.jetty.util.thread.ThreadPool;

import io.prometheus.client.exporter.MetricsServlet;
import io.prometheus.client.hotspot.DefaultExports;

public class PrometheusServer {
	
	public static final String PROMETHEUS_PORT = "prometheus.port";
	public static final int PROMETHEUS_PORT_DEFAULT = 9207;
	
	public static final String PROMETHEUS_THREAD_MIN = "prometheus.thread.max";
	public static final int PROMETHEUS_THREAD_MIN_DEFAULT = 4;
	
	public static final String PROMETHEUS_THREAD_MAX = "prometheus.thread.min";
	public static final int PROMETHEUS_THREAD_MAX_DEFAULT = 4;

	
	private static PrometheusServer instance = null;
	
	private Server server;
	
	private PrometheusServer() {
		
		this.server = new Server(this.getThreadPool());
		ServerConnector connector = new ServerConnector(this.server);
		connector.setPort(JMeterUtils.getPropDefault(PROMETHEUS_PORT, PROMETHEUS_PORT_DEFAULT));
		
		ServletContextHandler context = new ServletContextHandler();
		context.setContextPath("/");
		this.server.setHandler(context);
		context.addServlet(new ServletHolder(new MetricsServlet()), "/metrics");
		
		this.server.setConnectors(new ServerConnector[]{ connector });
		DefaultExports.initialize();
		
	}
	
	public static PrometheusServer getInstance() {
		if (instance == null) {
			instance = new PrometheusServer();
		}
		
		return instance;
	}
	
	private ThreadPool getThreadPool() {
        QueuedThreadPool threadPool = new QueuedThreadPool();
        threadPool.setMaxThreads(JMeterUtils.getPropDefault(PROMETHEUS_THREAD_MAX, PROMETHEUS_THREAD_MAX_DEFAULT));
        threadPool.setMinThreads(JMeterUtils.getPropDefault(PROMETHEUS_THREAD_MIN, PROMETHEUS_THREAD_MIN_DEFAULT));
        
        return threadPool;
	}
	
	public void start() throws Exception {
		this.server.start();
	}
	
	public void stop() throws Exception {
		this.server.stop();
	}
	

}
