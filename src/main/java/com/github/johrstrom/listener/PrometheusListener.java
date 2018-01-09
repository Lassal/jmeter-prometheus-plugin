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

import org.apache.jmeter.assertions.AssertionResult;
import org.apache.jmeter.engine.util.NoThreadClone;
import org.apache.jmeter.reporters.AbstractListenerElement;
import org.apache.jmeter.samplers.SampleEvent;
import org.apache.jmeter.samplers.SampleListener;
import org.apache.jmeter.testelement.TestStateListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


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

	private static final long serialVersionUID = -4833646252357876746L;

	private static final Logger log = LoggerFactory.getLogger(PrometheusListener.class);

	private transient PrometheusServer server = PrometheusServer.getInstance();
	private PrometheusListenerConfig config = new PrometheusListenerConfig();

	private PrometheusMetricUpdater updater =  PrometheusMetricUpdater.getInstance();
	

	/**
	 * Default Constructor.
	 */
	public PrometheusListener() {
		super();
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
			this.updater.updateSamplerMetric(event.getResult().getTime(), samplerLabelValues);
			this.updater.updateThreadMetric();


			// if there are any assertions to
			if (PrometheusListenerConfig.saveAssertions()) {
				if (event.getResult().getAssertionResults().length > 0) {
					for (AssertionResult assertionResult : event.getResult().getAssertionResults()) {
						String[] assertionsLabelValues = this.labelValues(event, assertionResult);
						
						this.updater.updateAssertionMetric(event.getResult().getTime(), assertionsLabelValues);						
					}
				}
			}

		} catch (Exception e) {
			log.error("Didn't update metric because of exception. Message was: {}", e.getMessage());
			log.debug("Didn't update metric because of exception", e);
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

		try {
			this.server.start();
		} catch (Exception e) {
			log.error("Couldn't  http server or scheduler", e);
		}

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
		int configLabelLength = config.getSamplerLabels().length;
		int totalLength = configLabelLength + sampleVarArr.length;
		
		String[] values = new String[totalLength];
		int valuesIndex = -1;	// at -1 so you can ++ when referencing it

		for (int i = 0; i < configLabelLength; i++) {
			Method m = this.config.getSamplerMethods()[i];
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
		int assertionLabelLength = config.getAssertionLabels().length;
		int sampleVariableLength = sampleVarArr.length;
		int combinedLength = assertionLabelLength + sampleVariableLength;
		
		String[] values = new String[combinedLength];

		for (int i = 0; i < assertionLabelLength; i++) {
			Method m = this.config.getAssertionMethods()[i];
			if (m.getDeclaringClass().equals(AssertionResult.class))
				values[i] = m.invoke(assertionResult).toString();
			else
				values[i] = m.invoke(event.getResult()).toString();
		}
		
		System.arraycopy(sampleVarArr, 0, values, assertionLabelLength, sampleVariableLength);

		if(log.isDebugEnabled())
			log.debug("assertion values: {}", Arrays.toString(values));
		
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
	
	

	

	

	

	

	
}
