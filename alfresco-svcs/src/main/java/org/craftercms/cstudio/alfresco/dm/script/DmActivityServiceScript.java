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
package org.craftercms.cstudio.alfresco.dm.script;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.sf.json.JSONObject;

import org.alfresco.repo.processor.BaseProcessorExtension;
import org.apache.commons.lang.StringUtils;
import org.craftercms.cstudio.alfresco.constant.CStudioConstants;
import org.craftercms.cstudio.alfresco.dm.constant.DmConstants;
import org.craftercms.cstudio.alfresco.dm.service.api.DmActivityService;
import org.craftercms.cstudio.alfresco.dm.to.DmContentItemTO;
import org.craftercms.cstudio.alfresco.service.ServicesManager;
import org.craftercms.cstudio.alfresco.service.api.ActivityService;
import org.craftercms.cstudio.alfresco.service.exception.ServiceException;
import org.craftercms.cstudio.alfresco.util.ContentFormatUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * exposes ActivityService to Alfresco javascript layer
 * 
 * @author hyanghee
 * @author Dejan Brkic
 * 
 */
public class DmActivityServiceScript extends BaseProcessorExtension {

	private static final Logger logger = LoggerFactory
			.getLogger(DmActivityServiceScript.class);

	protected ServicesManager _servicesManager;

	/**
	 * get user activities within the given site
	 * 
	 * @param site
	 * @param user
	 * @param num
	 * @param sort
	 * @param ascending
	 * @param excludeLive
	 * @return user activities
	 * @throws org.craftercms.cstudio.alfresco.service.exception.ServiceException
	 */
	public String getActivities(String site, String user, String num,
			String sort, boolean ascending, boolean excludeLive,
			String filterType) throws ServiceException {
		int numOfItems = ContentFormatUtils.getIntValue(num);
		List<DmContentItemTO> activities = _servicesManager.getService(DmActivityService.class).getActivities(site,
				user, numOfItems, sort, ascending, excludeLive, filterType);
		JSONObject jsonObject = new JSONObject();
		jsonObject.put(CStudioConstants.PROPERTY_TOTAL, activities.size());
		jsonObject.put(CStudioConstants.PROPERTY_SORTED_BY, sort);
		jsonObject.put(CStudioConstants.PROPERTY_SORT_ASCENDING,
				String.valueOf(ascending));
		jsonObject.put(CStudioConstants.PROPERTY_DOCUMENTS, activities);
		return jsonObject.toString();
	}

	/**
	 * post a user activity
	 *
	 * @param site site id
	 * @param user username
	 * @param key content path
	 * @param activityType CREATED, UPDATED or DELETED
	 * @param contentType content type (optional)
	 * @param extraInfo additional info map (optional)
	 */
	public void postActivity(String site, String user, String key, String activityType, String contentType, Map<String, String> extraInfo) {
		DmActivityService activityService = _servicesManager.getService(DmActivityService.class);
		ActivityService.ActivityType type = ActivityService.ActivityType.valueOf(activityType);
		if (extraInfo == null) {
			extraInfo = new HashMap<>();
		}
		if (!StringUtils.isEmpty(contentType)) {
			extraInfo.put(DmConstants.KEY_CONTENT_TYPE, contentType);
		}
		activityService.postActivity(site, user, key, type, extraInfo);
	}

	public void setServicesManager(ServicesManager servicesManager) {
		this._servicesManager = servicesManager;
	}
}
