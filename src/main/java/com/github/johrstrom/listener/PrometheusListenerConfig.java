package com.github.johrstrom.listener;

import java.lang.reflect.Method;
import java.util.ArrayList;

import io.prometheus.client.Collector;
import io.prometheus.client.Counter;
import io.prometheus.client.Summary;

import org.apache.jmeter.assertions.AssertionResult;
import org.apache.jmeter.samplers.SampleResult;
import org.apache.jmeter.util.JMeterUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PrometheusListenerConfig {
	
	public static final String PROMETHEUS_SAVE_LABEL = "prometheus.save.label";	
	public static final String PROMETHEUS_SAVE_CODE = "prometheus.save.code";	
	public static final String PROMETHEUS_SAVE_SUCCESS = "prometheus.save.success";	
	public static final String PROMETHEUS_SAVE_ASSERTIONS = "prometheus.save.assertions";	
	public static final String PROMETHEUS_SAVE_THREADS = "prometheus.save.threads";
	public static final String PROMETHEUS_ASSERTION_CLASS = "prometheus.assertion.class";
	public static final String PROMETHEUS_CLEAN_FREQUENCY = "prometheus.clean.freq";
	
	
	private String[] samplerLabels = initLabels();
	private Method[] samplerMethods = initSamplerMethods();
	
	private String[] assertionLabels = initLabels();
	private Method[] assertionMethods = initAssertionMethods();
	
	private static final Logger log = LoggerFactory.getLogger(PrometheusListener.class);
	
	
	public String[] getSamplerLabels() {
		return this.samplerLabels;
	}
	
	public String[] getAssertionLabels() {
		return this.assertionLabels;
	}
	
	public Method[] getAssertionMethods() {
		return this.assertionMethods;
	}
	
	public Method[] getSamplerMethods() {
		return this.samplerMethods;
	}
	
	public static boolean saveLabel() {
		return JMeterUtils.getPropDefault(PROMETHEUS_SAVE_LABEL, true);
	}

	public static boolean saveCode() {
		return JMeterUtils.getPropDefault(PROMETHEUS_SAVE_CODE, true);
	}

	public static boolean saveSuccess() {
		return JMeterUtils.getPropDefault(PROMETHEUS_SAVE_SUCCESS, true);
	}

	public static boolean saveAssertions() {
		return JMeterUtils.getPropDefault(PROMETHEUS_SAVE_ASSERTIONS, true);
	}

	public static boolean saveThreads() {
		return JMeterUtils.getPropDefault(PROMETHEUS_SAVE_THREADS, true);
	}
	
	public static long getCacheCleanFrequency() {
		return JMeterUtils.getPropDefault(PROMETHEUS_CLEAN_FREQUENCY, 0l);
	}

	public static Class<? extends Collector> getAssertionClass() {
		String classConfig = JMeterUtils.getPropDefault(PROMETHEUS_ASSERTION_CLASS, "counter");
		
		if (classConfig.equalsIgnoreCase("summary")){
			return Summary.class;
		}else {
			return Counter.class;	//just default to counter
		}
		
	}
	
	private static String[] initLabels() {
		
		ArrayList<String> labels = new ArrayList<>();
		
		if(saveLabel())
			labels.add("sampler_name");
		if(saveCode())
			labels.add("code");
		if(saveSuccess())
			labels.add("success");
				
		return labels.toArray(new String[labels.size()]);
	}
	
	private static Method[] initSamplerMethods() {
		ArrayList<Method> methods = new ArrayList<>();
		
		try {
			if(saveLabel())
				methods.add(SampleResult.class.getMethod("getSampleLabel"));
			if(saveCode())
				methods.add(SampleResult.class.getMethod("getResponseCode"));
			if(saveSuccess())
				methods.add(SampleResult.class.getMethod("isSuccessful"));
		} catch (NoSuchMethodException | SecurityException e) {
			log.error("Only partial reconfigure due to exception.", e);
		}
						
		return methods.toArray(new Method[methods.size()]);
	}
	
	private static Method[] initAssertionMethods() {
		
		ArrayList<Method> methods = new ArrayList<>();
		
		try {
			if(saveLabel())
				methods.add(AssertionResult.class.getMethod("getName"));
			if(saveCode())
				methods.add(SampleResult.class.getMethod("getResponseCode"));
			if(saveSuccess())
				methods.add(SampleResult.class.getMethod("isSuccessful"));
		} catch (NoSuchMethodException | SecurityException e) {
			e.printStackTrace();
		}
				
		return methods.toArray(new Method[methods.size()]);
	}

}
