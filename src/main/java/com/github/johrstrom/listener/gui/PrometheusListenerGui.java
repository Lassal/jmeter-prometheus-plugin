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
package com.github.johrstrom.listener.gui;

import java.awt.BorderLayout;

import javax.swing.JPanel;

import org.apache.jmeter.gui.util.VerticalPanel;
import org.apache.jmeter.testelement.TestElement;
import org.apache.jmeter.visualizers.gui.AbstractListenerGui;

import com.github.johrstrom.listener.PrometheusListener;


/**
 * The GUI class for the Prometheus Listener.
 * 
 * Currently, all configurations are done through properties files so this class
 * shows nothing visually other than comments.
 * 
 * @author Jeff Ohrstrom
 *
 */
public class PrometheusListenerGui extends AbstractListenerGui {

	private static final long serialVersionUID = 4984653136457108054L;
	public static final String SAVE_CONFIG = "johrstrom.prometheus.save_config";


	/**
	 * Default constructor
	 */
	public PrometheusListenerGui() {
		super();
		createGUI();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.apache.jmeter.gui.JMeterGUIComponent#createTestElement()
	 */
	public TestElement createTestElement() {
		PrometheusListener listener = new PrometheusListener();
		modifyTestElement(listener);
		listener.setProperty(TestElement.GUI_CLASS,
				com.github.johrstrom.listener.gui.PrometheusListenerGui.class.getName());
		listener.setProperty(TestElement.TEST_CLASS, com.github.johrstrom.listener.PrometheusListener.class.getName());

		return listener;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.apache.jmeter.gui.JMeterGUIComponent#getLabelResource()
	 */
	public String getLabelResource() {
		return getClass().getCanonicalName();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.apache.jmeter.gui.AbstractJMeterGuiComponent#getStaticLabel()
	 */
	@Override
	public String getStaticLabel() {
		return "Prometheus Listener";
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.apache.jmeter.gui.JMeterGUIComponent#modifyTestElement(org.apache.
	 * jmeter.testelement.TestElement)
	 */
	public void modifyTestElement(TestElement element) {
		super.configureTestElement(element);
	}


	/*
	 * (non-Javadoc)
	 * 
	 * @see org.apache.jmeter.gui.AbstractJMeterGuiComponent#getName()
	 */
	@Override
	public String getName() {
		if (super.getName() == null) {
			return this.getStaticLabel();
		} else {
			return super.getName();
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.apache.jmeter.gui.AbstractJMeterGuiComponent#configure(org.apache.
	 * jmeter.testelement.TestElement)
	 */
	@Override
	public void configure(TestElement element) {
		super.configure(element);

	}


	/**
	 * Private helper function to initialize all the Swing components.
	 */
	protected void createGUI() {
		setLayout(new BorderLayout(0, 5));
		setBorder(makeBorder());

		JPanel mainPanel = new JPanel();
		mainPanel.setLayout(new BorderLayout(0, 5));
		mainPanel.add(makeTitlePanel(), BorderLayout.NORTH);

		add(mainPanel, BorderLayout.NORTH);
		//add(createTopMostPanel(), BorderLayout.CENTER);
	}
	
	
	/**
	 * Create the panel that holds all the other panels (except for the title panel)
	 * 
	 * @return - the top most JPanel
	 */
	protected JPanel createTopMostPanel(){
		VerticalPanel panel = new VerticalPanel();
		
		return panel;
	}
	

}
