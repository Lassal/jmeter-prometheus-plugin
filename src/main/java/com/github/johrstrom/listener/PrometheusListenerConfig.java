package com.github.johrstrom.listener;

import java.io.Serializable;

import io.prometheus.client.Collector;
import io.prometheus.client.Counter;
import io.prometheus.client.Summary;

import org.apache.jmeter.util.JMeterUtils;

public class PrometheusListenerConfig implements Serializable {

	private static final long serialVersionUID = 3374323089879858706L;

	public static final String PROMETHEUS_SAVE_LABEL = "prometheus.save.label";	
	public static final String PROMETHEUS_SAVE_CODE = "prometheus.save.code";	
	public static final String PROMETHEUS_SAVE_SUCCESS = "prometheus.save.success";	
	public static final String PROMETHEUS_SAVE_ASSERTIONS = "prometheus.save.assertions";	
	public static final String PROMETHEUS_SAVE_THREADS = "prometheus.save.threads";
	
	public static final String PROMETHEUS_ASSERTION_CLASS = "prometheus.assertion.class";

	public boolean saveLabel() {
		return JMeterUtils.getPropDefault(PROMETHEUS_SAVE_LABEL, true);
	}

	public boolean saveCode() {
		return JMeterUtils.getPropDefault(PROMETHEUS_SAVE_CODE, true);
	}

	public boolean saveSuccess() {
		return JMeterUtils.getPropDefault(PROMETHEUS_SAVE_SUCCESS, true);
	}

	public boolean saveAssertions() {
		return JMeterUtils.getPropDefault(PROMETHEUS_SAVE_ASSERTIONS, true);
	}

	public boolean saveThreads() {
		return JMeterUtils.getPropDefault(PROMETHEUS_SAVE_THREADS, true);
	}

	public Class<? extends Collector> getAssertionClass() {
		String classConfig = JMeterUtils.getPropDefault(PROMETHEUS_ASSERTION_CLASS, "counter");
		
		if (classConfig.equalsIgnoreCase("summary")){
			return Summary.class;
		}else {
			return Counter.class;	//just default to counter
		}
		
	}

}
