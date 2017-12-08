/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed  under the  License is distributed on an "AS IS" BASIS,
 * WITHOUT  WARRANTIES OR CONDITIONS  OF ANY KIND, either  express  or
 * implied.
 *
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.johrstrom.listener;

import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.jmeter.assertions.AssertionResult;
import org.apache.jmeter.engine.util.NoThreadClone;
import org.apache.jmeter.reporters.AbstractListenerElement;
import org.apache.jmeter.samplers.SampleEvent;
import org.apache.jmeter.samplers.SampleListener;
import org.apache.jmeter.testelement.TestStateListener;
import org.apache.jmeter.testelement.property.ObjectProperty;
import org.apache.jmeter.threads.JMeterContextService;
import org.apache.jmeter.util.JMeterUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.johrstrom.util.CollectorConfig;

import io.prometheus.client.Collector;
import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.Counter;
import io.prometheus.client.Gauge;
import io.prometheus.client.SimpleCollector;
import io.prometheus.client.Summary;

/**
 * The main test element listener class of this library. Jmeter updates this
 * class through the SampleListener interface and it in turn updates the
 * CollectorRegistry. This class is also a TestStateListener to control when it
 * s up or shuts down the server that ultimately serves Prometheus the
 * results through an http api.
 * 
 * 
 * @author Jeff Ohrstrom
 *
 */
public class PrometheusListener extends AbstractListenerElement
		implements SampleListener, Serializable, TestStateListener, NoThreadClone {

	public static final String SAVE_CONFIG = "johrstrom.save_config";

	private static final long serialVersionUID = -4833646252357876746L;

	private static final Logger log = LoggerFactory.getLogger(PrometheusListener.class);

	private transient PrometheusServer server = PrometheusServer.getInstance();

	// Samplers
	private transient Summary samplerCollector;
	private CollectorConfig samplerConfig = new CollectorConfig();
	private boolean collectSamples = true;

	// Thread counter
	private transient Gauge threadCollector;
	private boolean collectThreads = true;

	// Assertions
	private transient Collector assertionsCollector;
	private CollectorConfig assertionConfig = new CollectorConfig();
	private boolean collectAssertions = true;
	
	private Map<List<String>, Long> latencyCache = new ConcurrentHashMap<>();
	private Map<List<String>, Long> assertionCache = new ConcurrentHashMap<>();
	
	public static final String PROMETHEUS_CLEAN_FREQUENCY = "prometheus.clean.freq";
	public static final long PROMETHEUS_CLEAN_FREQUENCY_DEFAULT = 0l;				//one hour in ms
	
	private CacheCleaner cacheCleaner;

	/**
	 * Default Constructor.
	 */
	public PrometheusListener() {
		this(new PrometheusSaveConfig());
	}

	/**
	 * Constructor with a configuration argument.
	 * 
	 * @param confifg
	 *            - the configuration to use.
	 */
	public PrometheusListener(PrometheusSaveConfig config) {
		super();
		this.setSaveConfig(config);
		
		long scheduledTime = JMeterUtils.getPropDefault(PROMETHEUS_CLEAN_FREQUENCY, PROMETHEUS_CLEAN_FREQUENCY_DEFAULT);
		if (scheduledTime > 0) {
			this.cacheCleaner = new CacheCleaner(this, scheduledTime);
			log.debug("Configured cache cleaner to run for {} seconds.", scheduledTime);
		}
		
		log.debug("Creating new prometheus listener.");
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.apache.jmeter.samplers.SampleListener#sampleOccurred(org.apache.
	 * jmeter.samplers.SampleEvent)
	 */
	public void sampleOccurred(SampleEvent event) {
		
		log.debug("sampleOccured with name {}", event.getResult().getSampleLabel());

		try {

			// build the label values from the event and observe the sampler
			// metrics
			String[] samplerLabelValues = this.labelValues(event);
			if (collectSamples) {
				samplerCollector.labels(samplerLabelValues).observe(event.getResult().getTime());
				this.updateLatencyCache(samplerLabelValues);
			}
				

			if (collectThreads)
				threadCollector.set(JMeterContextService.getContext().getThreadGroup().getNumberOfThreads());

			// if there are any assertions to
			if (collectAssertions) {
				if (event.getResult().getAssertionResults().length > 0) {
					for (AssertionResult assertionResult : event.getResult().getAssertionResults()) {
						String[] assertionsLabelValues = this.labelValues(event, assertionResult);
						
						if(assertionsCollector instanceof Summary) {
							((Summary) assertionsCollector).labels(assertionsLabelValues).observe(event.getResult().getTime());
							this.updateAssertionCache(assertionsLabelValues);
						}else if (assertionsCollector instanceof Counter) {
							((Counter) assertionsCollector).labels(assertionsLabelValues).inc();
						}						
					}
				}
			}

		} catch (Exception e) {
			log.error("Didn't update metric because of exception. Message was: {}", e.getMessage());
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.apache.jmeter.samplers.SampleListener#sampleed(org.apache.jmeter
	 * .samplers.SampleEvent)
	 */
	public void sampleStarted(SampleEvent arg0) {
		// do nothing
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.apache.jmeter.samplers.SampleListener#sampleStopped(org.apache.jmeter
	 * .samplers.SampleEvent)
	 */
	public void sampleStopped(SampleEvent arg0) {
		// do nothing
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.apache.jmeter.testelement.TestStateListener#testEnded()
	 */
	public void testEnded() {
		try {
			this.server.stop();
			this.cacheCleaner.interrupt();
		} catch (Exception e) {
			log.error("Couldn't stop http server or scheduler", e);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.apache.jmeter.testelement.TestStateListener#testEnded(java.lang.
	 * String)
	 */
	public void testEnded(String arg0) {
		this.testEnded();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.apache.jmeter.testelement.TestStateListener#tested()
	 */
	public void testStarted() {
		// update the configuration
		this.reconfigure();

		try {
			this.server.start();
			this.cacheCleaner.start();
		} catch (Exception e) {
			log.error("Couldn't  http server or scheduler", e);
		}

	}

	/**
	 * Set a new Save configuration. Note that this function reconfigures this
	 * object and one should not set the save config directly through
	 * {@link #setProperty(org.apache.jmeter.testelement.property.JMeterProperty)}
	 * functions.
	 * 
	 * @param config
	 *            - the configuration object
	 */
	public void setSaveConfig(PrometheusSaveConfig config) {
		this.setProperty(new ObjectProperty(SAVE_CONFIG, config));
		this.reconfigure();
	}

	/**
	 * Get the current Save configuration
	 * 
	 * @return
	 */
	public PrometheusSaveConfig getSaveConfig() {
		return (PrometheusSaveConfig) this.getProperty(SAVE_CONFIG).getObjectValue();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.apache.jmeter.testelement.TestStateListener#tested(java.lang.
	 * String)
	 */
	public void testStarted(String arg0) {
		this.testStarted();
	}


	/**
	 * For a given SampleEvent, get all the label values as determined by the
	 * configuration. Can return reflection related errors because this invokes
	 * SampleEvent accessor methods like getResponseCode or getSuccess.
	 * 
	 * @param event
	 *            - the event that occurred
	 * @return
	 * @throws IllegalAccessException
	 * @throws IllegalArgumentException
	 * @throws InvocationTargetException
	 */
	protected String[] labelValues(SampleEvent event)
			throws IllegalAccessException, IllegalArgumentException, InvocationTargetException {
		
		String[] sampleVarArr = this.sampleVariableValues(event);
		int configLabelLength = this.samplerConfig.getLabels().length;
		int totalLength = configLabelLength + sampleVarArr.length;
		
		String[] values = new String[totalLength];
		int valuesIndex = -1;	// at -1 so you can ++ when referencing it

		for (int i = 0; i < configLabelLength; i++) {
			Method m = this.samplerConfig.getMethods()[i];
			values[++valuesIndex] = m.invoke(event.getResult()).toString();
		}
		
		System.arraycopy(sampleVarArr, 0, values, configLabelLength, sampleVarArr.length);
		if(log.isDebugEnabled()) 
			log.debug("generated labelset {}", Arrays.toString(values));

		return values;

	}

	/**
	 * For a given SampleEvent and AssertionResult, get all the label values as
	 * determined by the configuration. Can return reflection related errors
	 * because this invokes SampleEvent accessor methods like getResponseCode or
	 * getSuccess.
	 * 
	 * @param event
	 *            - the event that occurred
	 * @param assertionResult
	 *            - the assertion results associated to the event
	 * @return
	 * @throws IllegalAccessException
	 * @throws IllegalArgumentException
	 * @throws InvocationTargetException
	 */
	protected String[] labelValues(SampleEvent event, AssertionResult assertionResult)
			throws IllegalAccessException, IllegalArgumentException, InvocationTargetException {

		String[] sampleVarArr = this.sampleVariableValues(event);
		int assertionLabelLength = this.assertionConfig.getLabels().length;
		int sampleVariableLength = sampleVarArr.length;
		int combinedLength = assertionLabelLength + sampleVariableLength;
		
		String[] values = new String[combinedLength];

		for (int i = 0; i < assertionLabelLength; i++) {
			Method m = this.assertionConfig.getMethods()[i];
			if (m.getDeclaringClass().equals(AssertionResult.class))
				values[i] = m.invoke(assertionResult).toString();
			else
				values[i] = m.invoke(event.getResult()).toString();
		}
		
		System.arraycopy(sampleVarArr, 0, values, assertionLabelLength, sampleVariableLength);

		log.info("assertion values: {}", (Object) values);
		
		return values;

	}
	
	private String[] sampleVariableValues(SampleEvent event) {
		int sampleVariableLength = SampleEvent.getVarCount();
		String[] values = new String[sampleVariableLength];
		
		for(int i = 0; i < sampleVariableLength; i++) {
			String varValue =  event.getVarValue(i);
			values[i] = (varValue == null) ?  "" : varValue;
		}
		
		return values;
	}

	/**
	 * Helper function to modify private member collectors and collector
	 * configurations. Any invocation of this method will modify them, even if
	 * configuration fails due to reflection errors, default configurations are
	 * applied and new collectors created.
	 */
	protected synchronized void reconfigure() {

		CollectorConfig tmpAssertConfig = new CollectorConfig();
		CollectorConfig tmpSamplerConfig = new CollectorConfig();

		// activate collections
		collectSamples = this.getSaveConfig().saveSuccess() || this.getSaveConfig().saveCode()
				|| this.getSaveConfig().saveLabel();
		collectThreads = this.getSaveConfig().saveThreads();
		collectAssertions = this.getSaveConfig().saveAssertions();

		try {
			// try to build new config objects
			tmpAssertConfig = this.newAssertionCollectorConfig();
			tmpSamplerConfig = this.newSamplerCollectorConfig();

		} catch (NoSuchMethodException | SecurityException e) {
			log.error("Only partial reconfigure due to exception.", e);
		}

		// remove old collectors and reassign member variables
		CollectorRegistry.defaultRegistry.clear();
		this.assertionConfig = tmpAssertConfig;
		this.samplerConfig = tmpSamplerConfig;

		// register new collectors
		this.createSamplerCollector();
		this.createAssertionCollector();

		if (collectThreads)
			this.threadCollector = Gauge.build().name("jmeter_running_threads").help("Counter for running threds")
					.create().register(CollectorRegistry.defaultRegistry);

		

		log.info("Reconfigure complete.");

		if (log.isDebugEnabled()) {
			log.debug("Assertion Configuration: " + this.assertionConfig.toString());
			log.debug("Sampler Configuration: " + this.samplerConfig.toString());
		}

	}

	/**
	 * Create a new CollectorConfig for Samplers. Due to reflection this throws
	 * errors based on security and absence of method definitions.
	 * 
	 * @return the new CollectorConfig
	 * @throws SecurityException
	 * @throws NoSuchMethodException
	 */
	protected CollectorConfig newSamplerCollectorConfig() throws NoSuchMethodException, SecurityException {
		PrometheusSaveConfig saveConfig = this.getSaveConfig();
		CollectorConfig collectorConfig = new CollectorConfig();

		if (saveConfig.saveLabel()) {
			collectorConfig.saveSamplerLabel();
		}

		if (saveConfig.saveCode()) {
			collectorConfig.saveSamlerCode();
		}

		if (saveConfig.saveSuccess()) {
			collectorConfig.saveSamplerSuccess();
		}

		return collectorConfig;
	}

	/**
	 * Create a new CollectorConfig for Assertions. Due to reflection this
	 * throws errors based on security and absence of method definitions.
	 * 
	 * @return
	 * @throws NoSuchMethodException
	 * @throws SecurityException
	 */
	protected CollectorConfig newAssertionCollectorConfig() throws NoSuchMethodException, SecurityException {
		PrometheusSaveConfig saveConfig = this.getSaveConfig();
		CollectorConfig collectorConfig = new CollectorConfig();

		if (saveConfig.saveAssertions()) {
			// TODO configure assertions more granularly
			collectorConfig.saveSamplerLabel();
			collectorConfig.saveAssertionFailure();
			collectorConfig.saveAssertionName();
		}

		return collectorConfig;
	}
	
	
	protected void createAssertionCollector(){
		if (!collectAssertions){
			return;
		}
		
		String[] labelNames = new String[]{};
		
		if (SampleEvent.getVarCount() > 0) {
			labelNames = this.combineAssertionLabelsWithSampleVars();
		}else {
			labelNames = this.assertionConfig.getLabels();
		}
		
		if(this.getSaveConfig().getAssertionClass().equals(Summary.class))
			this.assertionsCollector = Summary.build().name("jmeter_assertions_total").help("Counter for assertions")
				.labelNames(labelNames).quantile(0.5, 0.1).quantile(0.99, 0.1)
				.create().register(CollectorRegistry.defaultRegistry);
		
		else if(this.getSaveConfig().getAssertionClass().equals(Counter.class))
			this.assertionsCollector = Counter.build().name("jmeter_assertions_total").help("Counter for assertions")
			.labelNames(labelNames).create().register(CollectorRegistry.defaultRegistry);
			
	}

	
	protected void createSamplerCollector(){
		if (collectSamples) {
			String[] labelNames = new String[]{};
			
			if (SampleEvent.getVarCount() > 0) {
				labelNames = this.combineConfigLabelsWithSampleVars();
			}else {
				labelNames = this.samplerConfig.getLabels();
			}
			
			this.samplerCollector = Summary.build()
					.name("jmeter_samples_latency")
					.help("Summary for Sample Latency")
					.labelNames(labelNames)
					.quantile(0.5, 0.1)
					.quantile(0.99, 0.1)
					.create()
					.register(CollectorRegistry.defaultRegistry);
		}
	}
	
	private String[] combineAssertionLabelsWithSampleVars() {
		int assertionLabelLength = this.assertionConfig.getLabels().length;
		int sampleVariableLength = SampleEvent.getVarCount();
		int combinedLength = assertionLabelLength + sampleVariableLength;
		
		String[] returnArray = new String[combinedLength];
		int returnArrayIndex = -1;	// at -1 so you can ++ when referencing it
		
		//add config first
		String[] configuredLabels = this.assertionConfig.getLabels();
		for (int i = 0; i < assertionLabelLength; i++) {
			returnArray[++returnArrayIndex] = configuredLabels[i];
		}
		
		//now add sample variables
		for (int i = 0; i < sampleVariableLength; i++) {
			returnArray[++returnArrayIndex] = SampleEvent.getVarName(i);
		}
		
		return returnArray;
	}
	
	private String[] combineConfigLabelsWithSampleVars() {
		int configLabelLength = this.samplerConfig.getLabels().length;
		int sampleVariableLength = SampleEvent.getVarCount();
		int combinedLength = configLabelLength + sampleVariableLength;
		
		String[] returnArray = new String[combinedLength];
		int returnArrayIndex = -1;	// at -1 so you can ++ when referencing it
		
		//add config first
		String[] configuredLabels = this.samplerConfig.getLabels();
		for (int i = 0; i < configLabelLength; i++) {
			returnArray[++returnArrayIndex] = configuredLabels[i];
		}
		
		//now add sample variables
		for (int i = 0; i < sampleVariableLength; i++) {
			returnArray[++returnArrayIndex] = SampleEvent.getVarName(i);
		}
		
		return returnArray;
	}
	
	protected void updateLatencyCache(String...labels){
		this.updateCache(this.latencyCache, labels);
	}
	
	protected void updateAssertionCache(String...labels){
		this.updateCache(this.latencyCache, labels);
	}
	
	public void cleanCaches() {
		cleanCache(this.latencyCache, this.samplerCollector);
		cleanCache(this.assertionCache, (SimpleCollector<?>) this.assertionsCollector);
	}
	
	protected void updateCache(Map<List<String>, Long> cache, String...labels) {
		cache.put(Arrays.asList(labels), System.currentTimeMillis());
	}
	
	protected void cleanCache(Map<List<String>, Long> cache, SimpleCollector<?> collector) {
		long lastTime = System.currentTimeMillis() - 
				JMeterUtils.getPropDefault(PROMETHEUS_CLEAN_FREQUENCY, PROMETHEUS_CLEAN_FREQUENCY_DEFAULT);
		
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
	
	private class CacheCleaner extends Thread {
		private PrometheusListener listener;
		private long sleepTime;
		
		CacheCleaner(PrometheusListener listener, long sleepTime) {
			super("prometheus-cache-cleaner");
			this.listener = listener;
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
				this.listener.cleanCaches();
			}
			
		}
		
	}
	
}
