package com.github.johrstrom.listener;

import java.util.Arrays;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.List;

import org.apache.jmeter.samplers.SampleEvent;
import org.apache.jmeter.threads.JMeterContextService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.prometheus.client.Collector;
import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.Counter;
import io.prometheus.client.Gauge;
import io.prometheus.client.SimpleCollector;
import io.prometheus.client.Summary;

/**
 * A facade class for updating Prometheus Metrics. The facade exists so that it may 
 * keep a cache of when metrics with certain labels where updated and clear them from 
 * the {@link io.prometheus.client.CollectorRegistry} when they are older than a configurable
 * TTL (time to live). 
 * 
 * @author Jeff Ohrstrom
 *
 */
public class PrometheusMetricUpdater {
	
	private static PrometheusMetricUpdater instance = null;
	private PrometheusListenerConfig config = new PrometheusListenerConfig();
	
	private CacheCleaner cacheCleaner;
	
	private static Map<List<String>, Long> latencyCache = new ConcurrentHashMap<>();
	private static Map<List<String>, Long> assertionCache = new ConcurrentHashMap<>();
	
	private static final Logger log = LoggerFactory.getLogger(PrometheusMetricUpdater.class);
	
	// Samplers
	private transient Summary samplerCollector;

	// Thread counter
	private transient Gauge threadCollector;

	// Assertions
	private transient Collector assertionsCollector;
			
	private PrometheusMetricUpdater() {
		this.createAssertionCollector();
		this.createSamplerCollector();
		this.createThreadCollector();
		
		long scheduledTime = PrometheusListenerConfig.getCacheCleanFrequency();
		
		if (scheduledTime > 0) {
			cacheCleaner = new CacheCleaner(scheduledTime);
			log.debug("Configured cache cleaner to run for {} seconds.", scheduledTime);
			cacheCleaner.run();
		}
	}
	
	@Override
	protected synchronized void finalize() throws Throwable {
		super.finalize();
		
		if(this.cacheCleaner != null) {
			this.cacheCleaner.interrupt();
		}
		
		CollectorRegistry.defaultRegistry.clear();
		
	}

	public synchronized static PrometheusMetricUpdater getInstance() {
		if (instance == null) {
			instance = new PrometheusMetricUpdater();
		}
		
		return instance;
	}
	
	public void updateSamplerMetric(double value, String... labels) {
		this.samplerCollector.labels(labels).observe(value);
	}
	
	public void updateThreadMetric() {
		if (PrometheusListenerConfig.saveThreads())
			this.threadCollector.set(JMeterContextService.getContext().getThreadGroup().getNumberOfThreads());
	}
	
	public void updateAssertionMetric(double value, String... labels) {
		if(assertionsCollector instanceof Summary) {
			((Summary) assertionsCollector).labels(labels).observe(value);
		}else if (assertionsCollector instanceof Counter) {
			((Counter) assertionsCollector).labels(labels).inc();
		}
	}
	
	protected void createAssertionCollector(){
		if (!PrometheusListenerConfig.saveAssertions()){
			return;
		}
		
		String[] labelNames = new String[]{};
		
		if (SampleEvent.getVarCount() > 0) {
			labelNames = this.combineAssertionLabelsWithSampleVars();
		}else {
			labelNames = config.getAssertionLabels();
		}
		
		if(this.assertionsCollector != null) {
			CollectorRegistry.defaultRegistry.unregister(this.assertionsCollector);
		}
			
		
		if(PrometheusListenerConfig.getAssertionClass().equals(Summary.class))
			this.assertionsCollector = Summary.build().name("jmeter_assertions_total").help("Counter for assertions")
				.labelNames(labelNames).quantile(0.5, 0.1).quantile(0.99, 0.1)
				.create().register(CollectorRegistry.defaultRegistry);
		
		else if(PrometheusListenerConfig.getAssertionClass().equals(Counter.class))
			this.assertionsCollector = Counter.build().name("jmeter_assertions_total").help("Counter for assertions")
			.labelNames(labelNames).create().register(CollectorRegistry.defaultRegistry);
			
	}

	
	protected void createThreadCollector() {
		if (PrometheusListenerConfig.saveThreads())
			this.threadCollector = Gauge.build().name("jmeter_running_threads").help("Counter for running threds")
				.create().register(CollectorRegistry.defaultRegistry);
	}
	
	protected void createSamplerCollector(){
		
		String[] labelNames = new String[]{};
		
		if (SampleEvent.getVarCount() > 0) {
			labelNames = this.combineConfigLabelsWithSampleVars();
		}else {
			labelNames = this.config.getAssertionLabels();
		}
		
		if(this.samplerCollector != null)
			CollectorRegistry.defaultRegistry.unregister(this.samplerCollector);
		
		this.samplerCollector = Summary.build()
				.name("jmeter_samples_latency")
				.help("Summary for Sample Latency")
				.labelNames(labelNames)
				.quantile(0.5, 0.1)
				.quantile(0.99, 0.1)
				.create()
				.register(CollectorRegistry.defaultRegistry);
		
	}

	protected String[] combineAssertionLabelsWithSampleVars() {
		int assertionLabelLength = this.config.getAssertionLabels().length;
		int sampleVariableLength = SampleEvent.getVarCount();
		int combinedLength = assertionLabelLength + sampleVariableLength;
		
		String[] returnArray = new String[combinedLength];
		int returnArrayIndex = -1;	// at -1 so you can ++ when referencing it
		
		//add config first
		String[] configuredLabels = this.config.getAssertionLabels();
		for (int i = 0; i < assertionLabelLength; i++) {
			returnArray[++returnArrayIndex] = configuredLabels[i];
		}
		
		//now add sample variables
		for (int i = 0; i < sampleVariableLength; i++) {
			returnArray[++returnArrayIndex] = SampleEvent.getVarName(i);
		}
		
		return returnArray;
	}
	
	protected String[] combineConfigLabelsWithSampleVars() {
		int configLabelLength = this.config.getSamplerLabels().length;
		int sampleVariableLength = SampleEvent.getVarCount();
		int combinedLength = configLabelLength + sampleVariableLength;
		
		String[] returnArray = new String[combinedLength];
		int returnArrayIndex = -1;	// at -1 so you can ++ when referencing it
		
		//add config first
		String[] configuredLabels = this.config.getSamplerLabels();
		for (int i = 0; i < configLabelLength; i++) {
			returnArray[++returnArrayIndex] = configuredLabels[i];
		}
		
		//now add sample variables
		for (int i = 0; i < sampleVariableLength; i++) {
			returnArray[++returnArrayIndex] = SampleEvent.getVarName(i);
		}
		
		return returnArray;
	}
	
	
	protected void cleanCache(Map<List<String>, Long> cache, SimpleCollector<?> collector) {
		long lastTime = System.currentTimeMillis() - PrometheusListenerConfig.getCacheCleanFrequency();
		
		for (Entry<List<String>, Long> entry : cache.entrySet()) {
			if(entry.getValue() < lastTime) {
				collector.remove(entry.getKey().toArray(new String[] {}));
				cache.remove(entry.getKey());
				if(log.isDebugEnabled()) {
					log.debug("cleared cache with key {}", Arrays.toString(entry.getKey().toArray(new String[] {})));
				}
			}
		}
	}

	protected void updateLatencyCache(String...labels){
		updateCache(latencyCache, labels);
	}

	protected void updateAssertionCache(String...labels){
		updateCache(assertionCache, labels);
	}

	public void cleanCaches() {
		cleanCache(latencyCache, samplerCollector);
		cleanCache(assertionCache, (SimpleCollector<?>) assertionsCollector);
	}

	protected static void updateCache(Map<List<String>, Long> cache, String...labels) {
		cache.put(Arrays.asList(labels), System.currentTimeMillis());
	}

	private class CacheCleaner extends Thread {
		private long sleepTime;
		private PrometheusMetricUpdater updater = PrometheusMetricUpdater.getInstance();
		
		CacheCleaner(long sleepTime) {
			super("prometheus-cache-cleaner");
			this.sleepTime = sleepTime;
		}
	
		@Override
		public void run() {
			while(true) {
				try {
					Thread.sleep(sleepTime);
				} catch (InterruptedException e) {
					break;
				}
				updater.cleanCaches();
			}
			
		}
		
	}
}
