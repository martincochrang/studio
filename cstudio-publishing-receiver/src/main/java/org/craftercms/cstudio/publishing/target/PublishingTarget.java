/*
 * Copyright (C) 2007-2013 Crafter Software Corporation.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.craftercms.cstudio.publishing.target;

import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.craftercms.cstudio.publishing.processor.PublishingProcessor;

/**
 * publishing target
 * 
 * @author hyanghee
 * 
 */
public class PublishingTarget {

	private static Log LOGGER = LogFactory.getLog(PublishingTarget.class);
	
	/** target name **/
	private String name;
	/** target manager **/
	private TargetManager manager;
	/** target configuration parameters **/
	private Map<String, String> params;
	/** target publishing pre-processors **/
	private List<PublishingProcessor> preProcessors; // NOT USED
	/** target publishing post-processors **/
	private List<PublishingProcessor> postProcessors;

	private PublishingProcessor defaultPostProcessor = null;
	private boolean defaultProcessingEnabled = false;

	/**
	 * register self
	 * 
	 */
	public void register() {
		this.manager.register(this.name, this);
	}

	/**
	 * @return the params
	 */
	public Map<String, String> getParams() {
		return params;
	}

	/**
	 * @param params
	 *            the params to set
	 */
	public void setParams(Map<String, String> params) {
		this.params = params;
	}

	/**
	 * @return the preProcessors
	 */
	public List<PublishingProcessor> getPreProcessors() {
		return preProcessors;
	}

	/**
	 * @param preProcessors
	 *            the preProcessors to set
	 */
	public void setPreProcessors(List<PublishingProcessor> preProcessors) {
		this.preProcessors = preProcessors;
	}

	/**
	 * @return the postProcessors
	 */
	public List<PublishingProcessor> getPostProcessors() {
		return postProcessors;
	}

	/**
	 * @param postProcessors
	 *            the postProcessors to set
	 */
	public void setPostProcessors(List<PublishingProcessor> postProcessors) {
		this.postProcessors = postProcessors;
	}

    public PublishingProcessor getDefaultPostProcessor() { return defaultPostProcessor; }
    public void setDefaultPostProcessor(PublishingProcessor defaultPostProcessor) { this.defaultPostProcessor = defaultPostProcessor; }

    public boolean isDefaultProcessingEnabled() { return defaultProcessingEnabled; }
    public void setDefaultProcessingEnabled(boolean defaultProcessingEnabled) { this.defaultProcessingEnabled = defaultProcessingEnabled; }

    /**
	 * @return the manager
	 */
	public TargetManager getManager() {
		return manager;
	}

	/**
	 * @param manager
	 *            the manager to set
	 */
	public void setManager(TargetManager manager) {
		this.manager = manager;
	}

	/**
	 * @return the name
	 */
	public String getName() {
		return name;
	}

	/**
	 * @param name the name to set
	 */
	public void setName(String name) {
		this.name = name;
	}

	/**
	 * get target configuration parameter
	 * 
	 * @param name
	 * @return
	 */
	public String getParameter(String name) {
		if (this.params != null) {
			return this.params.get(name);
		} else {
			return null;
		}
	}
	
	/*
	 * (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	public String toString() {
		return this.name;
	}
}
