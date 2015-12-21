/*******************************************************************************
 * Crafter Studio Web-content authoring solution
 *     Copyright (C) 2007-2013 Crafter Software Corporation.
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <http://www.gnu.org/licenses/>.
 ******************************************************************************/
package org.craftercms.cstudio.alfresco.dm.script;

import javolution.util.FastList;
import net.sf.json.JSONArray;
import net.sf.json.JSONException;
import net.sf.json.JSONObject;
import org.alfresco.repo.processor.BaseProcessorExtension;
import org.alfresco.repo.transaction.RetryingTransactionHelper;
import org.alfresco.service.cmr.model.FileInfo;
import org.alfresco.service.cmr.repository.NodeRef;
import org.apache.commons.lang.StringUtils;
import org.craftercms.cstudio.alfresco.constant.CStudioConstants;
import org.craftercms.cstudio.alfresco.constant.CStudioContentModel;
import org.craftercms.cstudio.alfresco.dm.constant.DmConstants;
import org.craftercms.cstudio.alfresco.dm.service.api.*;
import org.craftercms.cstudio.alfresco.dm.service.impl.DependencyRules;
import org.craftercms.cstudio.alfresco.dm.to.DmContentItemTO;
import org.craftercms.cstudio.alfresco.dm.to.DmDependencyTO;
import org.craftercms.cstudio.alfresco.dm.to.DmError;
import org.craftercms.cstudio.alfresco.dm.to.DmPathTO;
import org.craftercms.cstudio.alfresco.dm.util.DmContentItemComparator;
import org.craftercms.cstudio.alfresco.dm.util.DmUtils;
import org.craftercms.cstudio.alfresco.dm.util.ThreadLocalContainer;
import org.craftercms.cstudio.alfresco.dm.workflow.MultiChannelPublishingContext;
import org.craftercms.cstudio.alfresco.dm.workflow.RequestContext;
import org.craftercms.cstudio.alfresco.dm.workflow.RequestContextBuilder;
import org.craftercms.cstudio.alfresco.service.ServicesManager;
import org.craftercms.cstudio.alfresco.service.api.*;
import org.craftercms.cstudio.alfresco.service.exception.ServiceException;
import org.craftercms.cstudio.alfresco.to.PublishingChannelConfigTO;
import org.craftercms.cstudio.alfresco.to.ResultTO;
import org.craftercms.cstudio.alfresco.util.ContentFormatUtils;
import org.craftercms.cstudio.alfresco.util.TransactionHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * A wrapper class of WorkflowService that exposes WCM WorkflowService in
 * Alfresco javascript API
 *
 * @author hyanghee
 * @author Dejan Brkic
 */
public class DmWorkflowServiceScript extends BaseProcessorExtension {

    private static final Logger logger = LoggerFactory.getLogger(DmWorkflowServiceScript.class);

    protected String JSON_KEY_ASSETS = "assets";
    protected String JSON_KEY_CHILDREN = "children";
    protected String JSON_KEY_COMPONENTS = "components";
    protected String JSON_KEY_DELETED = "deleted";
    protected String JSON_KEY_DOCUMENTS = "documents";
    protected String JSON_KEY_DELETED_ITEMS = "deletedItems";
    protected String JSON_KEY_IS_NOW = "now";
    protected String JSON_KEY_ITEMS = "items";
    protected String JSON_KEY_REASON = "reason";
    protected String JSON_KEY_SCHEDULED_DATE = "scheduledDate";
    protected String JSON_KEY_SUBMITTED_FOR_DELETION = "submittedForDeletion";
    protected String JSON_KEY_SUBMITTED = "submitted";
    protected String JSON_KEY_IN_PROGRESS = "inProgress";
    protected String JSON_KEY_IN_REFERENCE = "reference";
    protected String JSON_KEY_SEND_EMAIL = "sendEmail";
    protected String JSON_KEY_URI = "uri";
    protected String JSON_KEY_USER = "user";
    protected String JSON_KEY_RENDERING_TEMPLATES = "renderingTemplates";
    protected String JSON_KEY_LEVEL_DESCRIPTORS = "levelDescriptors";
    protected String JSON_KEY_PUBLISH_CHANNEL = "publishChannel";
    protected String JSON_KEY_STATUS_CHANNEL = "channels";
    protected String JSON_KEY_STATUS_SET = "status";
    protected String JSON_KEY_STATUS_MESSAGE = "message";
    protected String JSON_KEY_SUBMISSION_COMMENT = "submissionComment";

    protected ServicesManager _servicesManager;

    public ServicesManager getServicesManager() {
        return _servicesManager;
    }

    public void setServicesManager(ServicesManager servicesManager) {
        this._servicesManager = servicesManager;
    }

    private boolean deploymentEngine;

    public void setDeploymentEngine(boolean deploymentEngine) {
        this.deploymentEngine = deploymentEngine;
    }

    protected enum Operation {
        GOLIVE, DELETE
    }

    /**
     * submit items to go live queue by creating associations between the top
     * level items and their children and dependencies and add
     * cstudio-core-workflow:submitted aspect
     *
     * @param site
     * @param sub
     * @param request
     * @return call result
     * @throws org.craftercms.cstudio.alfresco.service.exception.ServiceException
     *
     */
    public ResultTO submitToGoLive(String site, String sub, String request) throws ServiceException {
        return submitForApproval(site, sub, request, false);
    }

    public ResultTO submitForApproval(final String site, final String sub, final String request, final boolean delete) throws ServiceException {
        DmTransactionService dmTransactionService = getServicesManager().getService(DmTransactionService.class);
        TransactionHelper transactionHelper = dmTransactionService.getTransactionHelper();
        ResultTO to = null;
        try {
            to = transactionHelper.doInTransaction(new RetryingTransactionHelper.RetryingTransactionCallback<ResultTO>() {
                        @Override
                        public ResultTO execute() throws Throwable {
                            return _submit(site, sub, request, delete);
                        }
                    });
            return to;
        } catch (ServiceException e) {
            if (logger.isErrorEnabled()) {
                logger.error("Error during submit", e);
            }
            throw e;
        }

    }

    protected ResultTO _submit(String site, String sub, String request, boolean delete) {
        PersistenceManagerService persistenceManagerService = getServicesManager().getService(PersistenceManagerService.class);
        ServicesConfig servicesConfig = getServicesManager().getService(ServicesConfig.class);
        String submittedBy = persistenceManagerService.getCurrentUserName();
        RequestContext requestContext = RequestContextBuilder.buildSubmitContext(site, servicesConfig, submittedBy);
        ResultTO result = new ResultTO();
        DmWorkflowService dmWorkflowService = getServicesManager().getService(DmWorkflowService.class);
        NotificationService notificationService = getServicesManager().getService(NotificationService.class);
        try {
            SimpleDateFormat format = new SimpleDateFormat(CStudioConstants.DATE_PATTERN_WORKFLOW);
            JSONObject requestObject = JSONObject.fromObject(request);
            boolean isNow = (requestObject.containsKey(JSON_KEY_IS_NOW)) ? requestObject.getBoolean(JSON_KEY_IS_NOW) : false;
            Date scheduledDate = null;
            if (!isNow) {
                scheduledDate = (requestObject.containsKey(JSON_KEY_SCHEDULED_DATE)) ? getScheduledDate(site, format, requestObject.getString(JSON_KEY_SCHEDULED_DATE)) : null;
            }
            boolean sendEmail = (requestObject.containsKey(JSON_KEY_SEND_EMAIL)) ? requestObject.getBoolean(JSON_KEY_SEND_EMAIL) : false;

            String submissionComment = (requestObject != null && requestObject.containsKey(JSON_KEY_SUBMISSION_COMMENT)) ? requestObject.getString(JSON_KEY_SUBMISSION_COMMENT) : null;
            // TODO: check scheduled date to make sure it is not null when isNow
            // = true and also it is not past
            JSONArray items = requestObject.getJSONArray(JSON_KEY_ITEMS);
            int length = items.size();
            String schDate = null;
            if (requestObject.containsKey(JSON_KEY_SCHEDULED_DATE)) {
                schDate = requestObject.getString(JSON_KEY_SCHEDULED_DATE);
            }
            List<String> itemsToDelete = new FastList<String>(length);
            if (length > 0) {
                List<DmDependencyTO> submittedItems = new ArrayList<DmDependencyTO>();
                for (int index = 0; index < length; index++) {
                    JSONObject item = items.getJSONObject(index);
                    DmDependencyTO submittedItem = getSubmittedItem(site, item, format, schDate);
                    String user = item.getString(JSON_KEY_USER);
                    submittedItems.add(submittedItem);
                    if (delete) {
                        submittedItem.setSubmittedForDeletion(true);
                    }
                }
                List<String> submittedPaths = new FastList<String>();
                String siteRootPath = servicesConfig.getRepositoryRootPath(site);
                for (DmDependencyTO goLiveItem : submittedItems) {
                    String fullPath = siteRootPath + goLiveItem.getUri();
                    submittedPaths.add(fullPath);
                    persistenceManagerService.setSystemProcessing(fullPath, true);
                    DependencyRules rule = new DependencyRules(site, getServicesManager());
                    Set<DmDependencyTO> depSet = rule.applySubmitRule(goLiveItem);
                    for (DmDependencyTO dep : depSet) {
                        String depPath = siteRootPath + dep.getUri();
                        submittedPaths.add(depPath);
                        persistenceManagerService.setSystemProcessing(depPath, true);
                    }
                }
                List<DmError> errors = dmWorkflowService.submitToGoLive(submittedItems, scheduledDate, sendEmail, delete, requestContext, submissionComment);
                result.setSuccess(true);
                result.setMessage(notificationService.getCompleteMessage(site, NotificationService.COMPLETE_SUBMIT_TO_GO_LIVE));
                for (String fullPath : submittedPaths) {
                    persistenceManagerService.setSystemProcessing(fullPath, false);
                }
            }
        } catch (Exception e) {
            result.setSuccess(false);
            result.setMessage(e.getMessage());
            if (logger.isErrorEnabled()) {
                logger.error(e.getMessage(), e);
            }
        }
        return result;
    }

    /**
     * parse the given date
     *
     * @param site
     * @param format
     * @param dateStr
     * @return date
     */
    public Date getScheduledDate(String site, SimpleDateFormat format, String dateStr) {
        ServicesConfig servicesConfig = getServicesManager().getService(ServicesConfig.class);
        return ContentFormatUtils.parseDate(format, dateStr, servicesConfig.getDefaultTimezone(site));
    }

    protected String getPublishChannel(JSONObject jsonPublishChannel) {
        return jsonPublishChannel.containsKey("name") ? jsonPublishChannel.getString("name") : null;
    }

    protected List<String> getStatusUpdateChannels(JSONArray jsonStatusChannels) {
        List<String> toRet = new FastList<String>();
        Iterator<JSONObject> iter = jsonStatusChannels.iterator();
        while (iter.hasNext()) {
            JSONObject channel = iter.next();
            toRet.add(channel.getString("name"));
        }
        return toRet;
    }

    /**
     * get a submitted item from a JSON item
     *
     * @param site
     * @param item
     * @param format
     * @return
     * @throws net.sf.json.JSONException
     */
    protected DmDependencyTO getSubmittedItem(String site, JSONObject item, SimpleDateFormat format, String globalSchDate) throws JSONException {
        PersistenceManagerService persistenceManagerService = getServicesManager().getService(PersistenceManagerService.class);
        DmDependencyTO submittedItem = new DmDependencyTO();
        String uri = item.getString(JSON_KEY_URI);
        submittedItem.setUri(uri);
        boolean deleted = (item.containsKey(JSON_KEY_DELETED)) ? item.getBoolean(JSON_KEY_DELETED) : false;
        submittedItem.setDeleted(deleted);
        boolean isNow = (item.containsKey(JSON_KEY_IS_NOW)) ? item.getBoolean(JSON_KEY_IS_NOW) : false;
        submittedItem.setNow(isNow);
        boolean submittedForDeletion = (item.containsKey(JSON_KEY_SUBMITTED_FOR_DELETION)) ? item.getBoolean(JSON_KEY_SUBMITTED_FOR_DELETION) : false;
        boolean submitted = (item.containsKey(JSON_KEY_SUBMITTED)) ? item.getBoolean(JSON_KEY_SUBMITTED) : false;
        boolean inProgress = (item.containsKey(JSON_KEY_IN_PROGRESS)) ? item.getBoolean(JSON_KEY_IN_PROGRESS) : false;
        boolean isReference = (item.containsKey(JSON_KEY_IN_REFERENCE)) ? item.getBoolean(JSON_KEY_IN_REFERENCE) : false;
        submittedItem.setReference(isReference);
        // boolean submittedForDeletion =
        // (item.containsKey(JSON_KEY_SUBMITTED_FOR_DELETION)) ?
        // item.getBoolean(JSON_KEY_SUBMITTED_FOR_DELETION) : false;
        submittedItem.setSubmittedForDeletion(submittedForDeletion);
        submittedItem.setSubmitted(submitted);
        submittedItem.setInProgress(inProgress);
        // TODO: check scheduled date to make sure it is not null when isNow =
        // true and also it is not past
        Date scheduledDate = null;
        if (globalSchDate != null && !StringUtils.isEmpty(globalSchDate)) {
            scheduledDate = getScheduledDate(site, format, globalSchDate);
        } else {
            if (item.containsKey(JSON_KEY_SCHEDULED_DATE)) {
                String dateStr = item.getString(JSON_KEY_SCHEDULED_DATE);
                if (!StringUtils.isEmpty(dateStr)) {
                    scheduledDate = getScheduledDate(site, format, dateStr);
                }
            }
        }
        if (scheduledDate == null && isNow == false) {
            submittedItem.setNow(true);
        }
        submittedItem.setScheduledDate(scheduledDate);
        JSONArray components = (item.containsKey(JSON_KEY_COMPONENTS)) ? item.getJSONArray(JSON_KEY_COMPONENTS) : null;
        List<DmDependencyTO> submittedComponents = getSubmittedItems(site, components, format, globalSchDate);
        submittedItem.setComponents(submittedComponents);

        JSONArray documents = (item.containsKey(JSON_KEY_DOCUMENTS)) ? item.getJSONArray(JSON_KEY_DOCUMENTS) : null;
        List<DmDependencyTO> submittedDocuments = getSubmittedItems(site, documents, format, globalSchDate);

        submittedItem.setDocuments(submittedDocuments);
        JSONArray assets = (item.containsKey(JSON_KEY_ASSETS)) ? item.getJSONArray(JSON_KEY_ASSETS) : null;
        List<DmDependencyTO> submittedAssets = getSubmittedItems(site, assets, format, globalSchDate);
        submittedItem.setAssets(submittedAssets);

        JSONArray templates = (item.containsKey(JSON_KEY_RENDERING_TEMPLATES)) ? item.getJSONArray(JSON_KEY_RENDERING_TEMPLATES) : null;
        List<DmDependencyTO> submittedTemplates = getSubmittedItems(site, templates, format, globalSchDate);
        submittedItem.setRenderingTemplates(submittedTemplates);

        JSONArray deletedItems = (item.containsKey(JSON_KEY_DELETED_ITEMS)) ? item.getJSONArray(JSON_KEY_DELETED_ITEMS) : null;
        List<DmDependencyTO> deletes = getSubmittedItems(site, deletedItems, format, globalSchDate);
        submittedItem.setDeletedItems(deletes);

        JSONArray children = (item.containsKey(JSON_KEY_CHILDREN)) ? item.getJSONArray(JSON_KEY_CHILDREN) : null;
        List<DmDependencyTO> submittedChidren = getSubmittedItems(site, children, format, globalSchDate);
        submittedItem.setChildren(submittedChidren);

        DmDependencyService dmDependencyService = getServicesManager().getService(DmDependencyService.class);

        if (uri.endsWith(DmConstants.XML_PATTERN)) {
            /**
             * Get dependent pages
             */
            DmDependencyTO dmDependencyTo = dmDependencyService.getDependenciesNoCalc(site, item.getString(JSON_KEY_URI), false, true);
            List<DmDependencyTO> dependentPages = new FastList<DmDependencyTO>();
            if (dmDependencyTo != null) {
                dependentPages = dmDependencyTo.getPages();
            }
            submittedItem.setPages(dependentPages);

            /**
             * Get Dependent Documents
             */
            if (submittedItem.getDocuments() == null) {
                List<DmDependencyTO> dependentDocuments = new FastList<DmDependencyTO>();
                if (dmDependencyTo != null) {
                    dmDependencyTo.getDocuments();
                }
                submittedItem.setDocuments(dependentDocuments);
            }

            /**
             * get sendEmail property if it is there
             */
            try {
                String fullPath = getFullPath(site, submittedItem);
                // PropertyValue sendEmailValue =
                // _avmService.getNodeProperty(-1, fullPath,
                // CStudioContentModel.PROP_WEB_WF_SEND_EMAIL);
                Serializable sendEmailValue = persistenceManagerService.getProperty(persistenceManagerService.getNodeRef(fullPath), CStudioContentModel.PROP_WEB_WF_SEND_EMAIL);
                boolean sendEmail = (sendEmailValue != null) ? Boolean.parseBoolean(sendEmailValue.toString()) : false;
                submittedItem.setSendEmail(sendEmail);

                String user = item.getString(JSON_KEY_USER);
                submittedItem.setSubmittedBy(user);
            } catch (Exception e) {
                e.printStackTrace(); // To change body of catch statement use
                // File | Settings | File Templates.
            }
        }

        return submittedItem;
    }

    /**
     * get submitted items from JSON request
     *
     * @param site
     * @param items
     * @param format
     * @return submitted items
     * @throws JSONException
     */
    protected List<DmDependencyTO> getSubmittedItems(String site, JSONArray items, SimpleDateFormat format, String schDate) throws JSONException {
        if (items != null) {
            int length = items.size();
            if (length > 0) {
                List<DmDependencyTO> submittedItems = new FastList<DmDependencyTO>();
                for (int index = 0; index < length; index++) {
                    JSONObject item = items.getJSONObject(index);
                    DmDependencyTO submittedItem = getSubmittedItem(site, item, format, schDate);
                    submittedItems.add(submittedItem);
                }
                return submittedItems;
            }
        }
        return null;
    }

    protected String getFullPath(String site, DmDependencyTO submittedItem) {
        DmContentService dmContentService = getServicesManager().getService(DmContentService.class);
        String uri = submittedItem.getUri();
        if (submittedItem.isDeleted()) {
            // if deleted, replace with the folder path
            uri = uri.replace("/" + DmConstants.INDEX_FILE, "");
        }
        String fullPath = dmContentService.getContentFullPath(site, uri);
        return fullPath;
    }

    /**
     * get go live items waiting for approval
     *
     * @param site
     * @param sub
     * @param sort
     * @param ascending
     * @param inProgressOnly
     * @return go live items in JSON
     * @throws ServiceException
     */
    public String getInProgressItems(String site, String sub, String sort, String ascending, String inProgressOnly) throws ServiceException {
        boolean asc = (StringUtils.isEmpty(ascending)) ? false : ascending.equalsIgnoreCase("true");
        boolean inProgress = (inProgressOnly != null && inProgressOnly.equals("true")) ? true : false;
        DmContentItemComparator comparator = new DmContentItemComparator(sort, asc, true, true);
        DmWorkflowService dmWorkflowService = getServicesManager().getService(DmWorkflowService.class);
        List<DmContentItemTO> items = dmWorkflowService.getInProgressItems(site, sub, comparator, inProgress);
        JSONObject jsonObject = new JSONObject();
        int total = 0;
        if (items != null) {
            for (DmContentItemTO item : items) {
                total += item.getNumOfChildren();
            }
        }
        jsonObject.put(CStudioConstants.PROPERTY_TOTAL, total);
        jsonObject.put(CStudioConstants.PROPERTY_SORTED_BY, sort);
        jsonObject.put(CStudioConstants.PROPERTY_SORT_ASCENDING, String.valueOf(asc));
        jsonObject.put(CStudioConstants.PROPERTY_DOCUMENTS, items);
        return jsonObject.toString();
    }

    /**
     * get go live items waiting for approval
     *
     * @param site
     * @param sub
     * @param sort
     * @param ascending
     * @return go live items in JSON
     * @throws ServiceException
     */
    public String getGoLiveItems(final String site, final String sub, final String sort, final String ascending) throws ServiceException {
        DmTransactionService dmTransactionService = getServicesManager().getService(DmTransactionService.class);
        TransactionHelper transactionHelper = dmTransactionService.getTransactionHelper();
        try {
            return transactionHelper.doInTransaction(new RetryingTransactionHelper.RetryingTransactionCallback<String>() {
                        @Override
                        public String execute() throws Throwable {
                            try {
                                boolean asc = (StringUtils.isEmpty(ascending)) ? false : ascending.equalsIgnoreCase("true");
                                DmContentItemComparator comparator = new DmContentItemComparator(sort, asc, false, false);
                                DmWorkflowService dmWorkflowService = getServicesManager().getService(DmWorkflowService.class);
                                List<DmContentItemTO> items = dmWorkflowService.getGoLiveItems(site, sub, comparator);
                                JSONObject jsonObject = new JSONObject();
                                int total = 0;
                                if (items != null) {
                                    for (DmContentItemTO item : items) {
                                        total += item.getNumOfChildren();
                                    }
                                }
                                jsonObject.put(CStudioConstants.PROPERTY_TOTAL, total);
                                jsonObject.put(CStudioConstants.PROPERTY_SORTED_BY, sort);
                                jsonObject.put(CStudioConstants.PROPERTY_SORT_ASCENDING, String.valueOf(asc));
                                jsonObject.put(CStudioConstants.PROPERTY_DOCUMENTS, items);
                                return jsonObject.toString();
                            } catch (ServiceException e) {
                                if (logger.isErrorEnabled()) {
                                    logger.error("Error fetching go live queue", e);
                                }
                                throw e;
                            } catch (RuntimeException e) {
                                if (logger.isErrorEnabled()) {
                                    logger.error("Error fetching go live queue", e);
                                }
                                throw e;
                            }
                        }
                    });
        } catch (ServiceException e) {
            if (logger.isErrorEnabled()) {
                logger.error("Error fetching go live", e);
            }
            throw e;
        } catch (RuntimeException e) {
            if (logger.isErrorEnabled()) {
                logger.error("Error fetching go live", e);
            }
            throw e;
        }

    }

    /**
     * approve workflows and schedule them as specified in the request
     *
     * @param site
     * @param sub
     * @param request
     * @return call result
     * @throws ServiceException
     */
    public ResultTO goLive(final String site, final String sub, final String request) throws ServiceException {
        GeneralLockService generalLockService = getServicesManager().getService(GeneralLockService.class);
        String lockKey = DmConstants.PUBLISHING_LOCK_KEY.replace("{SITE}", site.toUpperCase());
        generalLockService.lock(lockKey);
        try {
            DmTransactionService dmTransactionService = getServicesManager().getService(DmTransactionService.class);
            TransactionHelper transactionHelper = dmTransactionService.getTransactionHelper();
            try {
                return approve(site, sub, request, Operation.GOLIVE);
            } catch (RuntimeException e) {
                if (logger.isErrorEnabled()) {
                    logger.error("error making go live", e);
                }
                throw e;
            }
        } catch (RuntimeException e) {
            throw e;
        } finally {
            generalLockService.unlock(lockKey);
        }
    }

    /**
     * approve workflows and schedule them as specified in the request
     *
     * @param site
     * @param sub
     * @param request
     * @return call result
     * @throws ServiceException
     */
    public ResultTO approve(String site, String sub, String request, Operation operation) {
        PersistenceManagerService persistenceManagerService = getServicesManager().getService(PersistenceManagerService.class);
        String approver = persistenceManagerService.getCurrentUserName();
        ResultTO result = new ResultTO();
        try {
            JSONObject requestObject = JSONObject.fromObject(request);
            JSONArray items = requestObject.getJSONArray(JSON_KEY_ITEMS);
            String scheduledDate = null;
            if (requestObject.containsKey(JSON_KEY_SCHEDULED_DATE)) {
                scheduledDate = requestObject.getString(JSON_KEY_SCHEDULED_DATE);
            }
            boolean isNow = (requestObject.containsKey(JSON_KEY_IS_NOW)) ? requestObject.getBoolean(JSON_KEY_IS_NOW) : false;

            String publishChannelGroupName = (requestObject.containsKey(JSON_KEY_PUBLISH_CHANNEL)) ? getPublishChannel(requestObject.getJSONObject(JSON_KEY_PUBLISH_CHANNEL)) : null;
            JSONObject jsonObjectStatus = requestObject.getJSONObject(JSON_KEY_STATUS_SET);
            // List<String> statusUpdateChannelNames = (jsonObjectStatus != null
            // && jsonObjectStatus.containsKey(JSON_KEY_STATUS_CHANNEL)) ?
            // getStatusUpdateChannels(jsonObjectStatus.getJSONArray(JSON_KEY_STATUS_CHANNEL))
            // : (new FastList<String>());
            String statusMessage = (jsonObjectStatus != null && jsonObjectStatus.containsKey(JSON_KEY_STATUS_MESSAGE)) ? jsonObjectStatus.getString(JSON_KEY_STATUS_MESSAGE) : null;
            String submissionComment = (requestObject != null && requestObject.containsKey(JSON_KEY_SUBMISSION_COMMENT)) ? requestObject.getString(JSON_KEY_SUBMISSION_COMMENT) : "Test Go Live";
            MultiChannelPublishingContext mcpContext = new MultiChannelPublishingContext(publishChannelGroupName, statusMessage, submissionComment);
            DmPublishService publishingService = getServicesManager().getService(DmPublishService.class);
            if(operation!=Operation.DELETE && !publishingService.hasChannelsConfigure(site, mcpContext)){
            	ResultTO toReturn = new ResultTO();
            	List<PublishingChannelConfigTO> channelsList = getServicesManager().getService(SiteService.class).getPublishingChannelGroupConfigs(site).get(mcpContext.getPublishingChannelGroup()).getChannels();
            	String channels=StringUtils.join(channelsList, " ");
            	toReturn.setMessage(" Specified target '"+channels+"' was not found. Please check if an endpoint or channel with name '"+channels+"' exists in site configuration");
            	toReturn.setSuccess(false);
            	toReturn.setInvalidateCache(false);
            	return toReturn;
            }
            
            
            
            int length = items.size();
            if (length == 0) {
                throw new ServiceException("No items provided to go live.");
            }

            String responseMessageKey = null;
            SimpleDateFormat format = new SimpleDateFormat(CStudioConstants.DATE_PATTERN_WORKFLOW);
            List<DmDependencyTO> submittedItems = new FastList<DmDependencyTO>();
            for (int index = 0; index < length; index++) {
                JSONObject item = items.getJSONObject(index);
                DmDependencyTO submittedItem = getSubmittedItem(site, item, format, scheduledDate);
                List<DmDependencyTO> submitForDeleteChildren = removeSubmitToDeleteChildrenForGoLive(submittedItem, operation);
                if (submittedItem.isReference()) {
                    submittedItem.setReference(false);
                }
                submittedItems.add(submittedItem);
                submittedItems.addAll(submitForDeleteChildren);
            }
            // AuthenticationUtil.setFullyAuthenticatedUser(approver);
            ServicesConfig servicesConfig = getServicesManager().getService(ServicesConfig.class);
            DmRenameService dmRenameService = getServicesManager().getService(DmRenameService.class);
            DmWorkflowService dmWorkflowService = getServicesManager().getService(DmWorkflowService.class);
            switch (operation) {
                case GOLIVE:
                    if (scheduledDate != null && isNow == false) {
                        responseMessageKey = NotificationService.COMPLETE_SCHEDULE_GO_LIVE;
                    } else {
                        responseMessageKey = NotificationService.COMPLETE_GO_LIVE;
                    }
                    List<DmDependencyTO> submitToDeleteItems = new FastList<DmDependencyTO>();
                    List<DmDependencyTO> goLiveItems = new FastList<DmDependencyTO>();
                    List<DmDependencyTO> renameItems = new FastList<DmDependencyTO>();
                    for (DmDependencyTO item : submittedItems) {
                        if (item.isSubmittedForDeletion()) {
                            submitToDeleteItems.add(item);
                        } else {
                            if (!dmRenameService.isItemRenamed(site, item)) {
                                goLiveItems.add(item);
                            } else {
                                renameItems.add(item);
                            }
                        }
                    }

                    if (!submitToDeleteItems.isEmpty()) {
                        dmWorkflowService.doDelete(site, sub, submitToDeleteItems, approver);
                    }

                    if (!goLiveItems.isEmpty()) {
                        List<DmDependencyTO> references = getRefAndChildOfDiffDateFromParent(site, goLiveItems, true);
                        List<DmDependencyTO> children = getRefAndChildOfDiffDateFromParent(site, goLiveItems, false);
                        goLiveItems.addAll(references);
                        goLiveItems.addAll(children);
                        List<String> goLivePaths = new FastList<String>();
                        for (DmDependencyTO goLiveItem : goLiveItems) {
                            resolveSubmittedPaths(site, goLiveItem, goLivePaths);
                        }
                        List<String> nodeRefs = new FastList<String>();
                        GeneralLockService generalLockService = getServicesManager().getService(GeneralLockService
                            .class);
                        for (String fullPath : goLivePaths) {
                            NodeRef nr = persistenceManagerService.getNodeRef(fullPath);
                            if (nr != null) {
                                nodeRefs.add(nr.getId());
                                generalLockService.lock(nr.getId());
                            }
                        }
                        try {
                            dmWorkflowService.goLive(site, sub, goLiveItems, approver, mcpContext);
                        } finally {
                            for (String nr : nodeRefs) {
                                generalLockService.unlock(nr);
                            }
                        }
                    }

                    if (!renameItems.isEmpty()) {
                        List<String> renamePaths = new FastList<String>();
                        List<DmDependencyTO> renamedChildren = new FastList<DmDependencyTO>();
                        for (DmDependencyTO renameItem : renameItems) {
                            renamedChildren.addAll(getChildrenForRenamedItem(site, renameItem));
                            String fullPath = servicesConfig.getRepositoryRootPath(site) + renameItem.getUri();
                            renamePaths.add(fullPath);
                            persistenceManagerService.setSystemProcessing(fullPath, true);
                        }
                        for (DmDependencyTO renamedChild : renamedChildren) {
                            String fullPath = servicesConfig.getRepositoryRootPath(site) + renamedChild.getUri();
                            renamePaths.add(fullPath);
                            persistenceManagerService.setSystemProcessing(fullPath, true);
                        }
                        renameItems.addAll(renamedChildren);
                        //Set proper information of all renameItems before send them to GoLive 
                        for(int i=0;i<renameItems.size();i++){
                            DmDependencyTO renamedItem = renameItems.get(i);
                            if (renamedItem.getScheduledDate() != null && renamedItem.getScheduledDate().after(new Date())) {
                                renamedItem.setNow(false);
                            } else {
                                renamedItem.setNow(true);
                            }
                            renameItems.set(i, renamedItem);
                        }

                        dmRenameService.goLive(site, sub, renameItems, approver, mcpContext);
                    }

                    break;
                case DELETE:
                    responseMessageKey = NotificationService.COMPLETE_DELETE;
                    List<String> deletePaths = new FastList<String>();
                    List<String> nodeRefs = new ArrayList<String>();
                    for (DmDependencyTO deletedItem : submittedItems) {
                        String fullPath = servicesConfig.getRepositoryRootPath(site) + deletedItem.getUri();
                        //deletedItem.setScheduledDate(getScheduledDate(site, format, scheduledDate));
                        deletePaths.add(fullPath);
                        NodeRef nodeRef = persistenceManagerService.getNodeRef(fullPath);
                        if (nodeRef != null) {
                            nodeRefs.add(nodeRef.getId());
                        }
                    }
                    dmWorkflowService.doDelete(site, sub, submittedItems, approver);
            }
            result.setSuccess(true);
            NotificationService notificationService = getServicesManager().getService(NotificationService.class);
            result.setMessage(notificationService.getCompleteMessage(site, responseMessageKey));

        } catch (JSONException e) {
            if (logger.isErrorEnabled()) {
                logger.error("error performing operation " + operation + " " + e);
            }
            result.setSuccess(false);
            result.setMessage(e.getMessage());
        } catch (ServiceException e) {
            if (logger.isErrorEnabled()) {
                logger.error("error performing operation " + operation + " " + e);
            }
            result.setSuccess(false);
            result.setMessage(e.getMessage());
        }
        return result;
    }

    protected List<DmDependencyTO> getChildrenForRenamedItem(String site, DmDependencyTO renameItem) {
        ServicesConfig servicesConfig = getServicesManager().getService(ServicesConfig.class);
        PersistenceManagerService persistenceManagerService = getServicesManager().getService(PersistenceManagerService.class);
        String siteRoot = servicesConfig.getRepositoryRootPath(site);
        List<DmDependencyTO> toRet = new FastList<DmDependencyTO>();
        List<DmDependencyTO> children = renameItem.getChildren();
        Date date = renameItem.getScheduledDate();
        if (children != null) {
            Iterator<DmDependencyTO> childItr = children.iterator();
            while (childItr.hasNext()) {
                DmDependencyTO child = childItr.next();
                Date pageDate = child.getScheduledDate();
                if ((date == null && pageDate != null) || (date != null && !date.equals(pageDate))) {
                    if (!renameItem.isNow()) {
                        child.setNow(false);
                        if (date != null && (pageDate != null && pageDate.before(date))) {
                            child.setScheduledDate(date);
                        }
                    }
                    toRet.add(child);
                    List<DmDependencyTO> childDeps = child.flattenChildren();
                    for (DmDependencyTO childDep : childDeps) {
                        String depPath = siteRoot + childDep.getUri();
                        ObjectStateService.State depState = persistenceManagerService.getObjectState(depPath);
                        if (ObjectStateService.State.isUpdateOrNew(depState)) {
                            toRet.add(childDep);
                        }
                    }
                    child.setReference(false);
                    childItr.remove();
                }
            }
        }
        return toRet;
    }

    protected List<DmDependencyTO> getChildrenDependenciesForRenamedItem(String site, FileInfo fileInfo) {
        List<DmDependencyTO> toRet = new FastList<DmDependencyTO>();
        PersistenceManagerService persistenceManagerService = getServicesManager().getService(PersistenceManagerService.class);
        DmDependencyService dmDependencyService = getServicesManager().getService(DmDependencyService.class);
        if (fileInfo.isFolder()) {
            List<FileInfo> children = persistenceManagerService.list(fileInfo.getNodeRef());
            for (FileInfo childInfo : children) {
                toRet.addAll(getChildrenDependenciesForRenamedItem(site, childInfo));
            }
        } else {
            DmPathTO childPathTO = new DmPathTO(persistenceManagerService.getNodePath(fileInfo.getNodeRef()));
            DmDependencyTO childDep = dmDependencyService.getDependenciesNoCalc(site, childPathTO.getRelativePath(), false, true);
            if (childDep != null) {
                toRet.add(childDep);
            }
        }
        return toRet;
    }

    protected void resolveSubmittedPaths(String site, DmDependencyTO item, List<String> submittedPaths) {
        ServicesConfig servicesConfig = getServicesManager().getService(ServicesConfig.class);
        PersistenceManagerService persistenceManagerService = getServicesManager().getService(PersistenceManagerService.class);
        String fullPath = servicesConfig.getRepositoryRootPath(site) + item.getUri();
        if (!submittedPaths.contains(fullPath)) {
            submittedPaths.add(fullPath);
        }
        List<DmDependencyTO> children = item.getChildren();
        if (children != null) {
            for (DmDependencyTO child : children) {
                String childPath = servicesConfig.getRepositoryRootPath(site) + child.getUri();
                ObjectStateService.State depState = persistenceManagerService.getObjectState(childPath);
                if (ObjectStateService.State.isUpdateOrNew(depState)) {
                    if (!submittedPaths.contains(childPath)) {
                        submittedPaths.add(childPath);
                    }
                    resolveSubmittedPaths(site, child, submittedPaths);
                }
            }
        }

        DependencyRules rule = new DependencyRules(site, getServicesManager());
        Set<DmDependencyTO> deps = rule.applySubmitRule(item);
        if (deps != null) {
            for (DmDependencyTO dep : deps) {
                String depPath = servicesConfig.getRepositoryRootPath(site) + dep.getUri();
                ObjectStateService.State depState = persistenceManagerService.getObjectState(depPath);
                if (ObjectStateService.State.isUpdateOrNew(depState)) {
                    if (!submittedPaths.contains(depPath)) {
                        submittedPaths.add(depPath);
                    }
                }
                resolveSubmittedPaths(site, dep, submittedPaths);
            }
        }

    }

    protected List<DmDependencyTO> getRefAndChildOfDiffDateFromParent(String site, List<DmDependencyTO> submittedItems, boolean removeInPages) {
        ServicesConfig servicesConfig = getServicesManager().getService(ServicesConfig.class);
        PersistenceManagerService persistenceManagerService = getServicesManager().getService(PersistenceManagerService.class);
        String siteRoot = servicesConfig.getRepositoryRootPath(site);
        List<DmDependencyTO> childAndReferences = new FastList<DmDependencyTO>();
        for (DmDependencyTO submittedItem : submittedItems) {
            List<DmDependencyTO> children = submittedItem.getChildren();
            Date date = submittedItem.getScheduledDate();
            if (children != null) {
                Iterator<DmDependencyTO> childItr = children.iterator();
                while (childItr.hasNext()) {
                    DmDependencyTO child = childItr.next();
                    Date pageDate = child.getScheduledDate();
                    if ((date == null && pageDate != null) || (date != null && !date.equals(pageDate))) {
                        if (!submittedItem.isNow()) {
                            child.setNow(false);
                            if (date != null && (pageDate != null && pageDate.before(date))) {
                                child.setScheduledDate(date);
                            }
                        }
                        childAndReferences.add(child);
                        List<DmDependencyTO> childDeps = child.flattenChildren();
                        for (DmDependencyTO childDep : childDeps) {
                            String depPath = siteRoot + childDep.getUri();
                            ObjectStateService.State depState = persistenceManagerService.getObjectState(depPath);
                            if (ObjectStateService.State.isUpdateOrNew(depState)) {
                                childAndReferences.add(childDep);
                            }
                        }
                        child.setReference(false);
                        childItr.remove();
                        if (removeInPages) {
                            String uri = child.getUri();
                            List<DmDependencyTO> pages = submittedItem.getPages();
                            if (pages != null) {
                                Iterator<DmDependencyTO> pagesIter = pages.iterator();
                                while (pagesIter.hasNext()) {
                                    DmDependencyTO page = pagesIter.next();
                                    if (page.getUri().equals(uri)) {
                                        pagesIter.remove();
                                    }
                                }
                            }
                        }
                    }
                }
            }
			/*
			 * List<DmDependencyTO> deps =
			 * submittedItem.getDirectDependencies(); if (deps != null) {
			 * Iterator<DmDependencyTO> depItr = deps.iterator(); while
			 * (depItr.hasNext()) { DmDependencyTO dep = depItr.next(); String
			 * depPath = siteRoot + dep.getUri(); ObjectStateService.State
			 * depState = persistenceManagerService.getObjectState(depPath); if
			 * (ObjectStateService.State.isUpdateOrNew(depState)) { Date
			 * pageDate = dep.getScheduledDate(); if ( (date==null &&
			 * pageDate!=null) || (date!=null && !date.equals(pageDate))) {
			 * childAndReferences.add(dep); dep.setReference(false);
			 * depItr.remove(); if (removeInPages) { String uri = dep.getUri();
			 * List<DmDependencyTO> pages = submittedItem.getPages(); if (pages
			 * != null) { Iterator<DmDependencyTO> pagesIter = pages.iterator();
			 * while (pagesIter.hasNext()) { DmDependencyTO page =
			 * pagesIter.next(); if (page.getUri().equals(uri)) {
			 * pagesIter.remove(); } } } } } }
			 * childAndReferences.addAll(getRefAndChildOfDiffDateFromParent
			 * (site, dep.getDirectDependencies(), removeInPages)); } }
			 */
            DependencyRules rule = new DependencyRules(site, getServicesManager());
            childAndReferences.addAll(rule.applySubmitRule(submittedItem));
        }
        return childAndReferences;
    }

    /**
     * removes the child items which are in submit to delete state from
     * submitted items as these have to be routed for deletion. it applies to
     * GoLive operation.
     *
     * @param dependencyTO
     * @param operation
     * @return
     */
    protected List<DmDependencyTO> removeSubmitToDeleteChildrenForGoLive(DmDependencyTO dependencyTO, Operation operation) {
        List<DmDependencyTO> submitForDeleteChilds = new FastList<DmDependencyTO>();
        if (operation == Operation.GOLIVE && !dependencyTO.isSubmittedForDeletion()) {
            List<DmDependencyTO> children = dependencyTO.getChildren();
            if (children != null) {
                for (DmDependencyTO child : children) {
                    if (child.isSubmittedForDeletion()) {
                        submitForDeleteChilds.add(child);
                    }
                }
                for (DmDependencyTO submitForDeleteChild : submitForDeleteChilds) {
                    children.remove(submitForDeleteChild);
                }
            }
        }
        return submitForDeleteChilds;
    }

    /**
     * reject items that are currently in workflow and send a rejection notice
     * to the user
     *
     * @param site
     * @param sub
     * @param request
     * @return call result
     * @throws ServiceException
     */
    public ResultTO reject(String site, String sub, String request) {
        ResultTO result = new ResultTO();
        PersistenceManagerService persistenceManagerService = getServicesManager().getService(PersistenceManagerService.class);
        try {
            String approver = persistenceManagerService.getCurrentUserName();
            JSONObject requestObject = JSONObject.fromObject(request);
            String reason = (requestObject.containsKey(JSON_KEY_REASON)) ? requestObject.getString(JSON_KEY_REASON) : "";
            JSONArray items = requestObject.getJSONArray(JSON_KEY_ITEMS);
            String scheduledDate = null;
            if (requestObject.containsKey(JSON_KEY_SCHEDULED_DATE)) {
                scheduledDate = requestObject.getString(JSON_KEY_SCHEDULED_DATE);
            }
            int length = items.size();
            ObjectStateService objectStateService = getServicesManager().getService(ObjectStateService.class);
            ServicesConfig servicesConfig = getServicesManager().getService(ServicesConfig.class);
            if (length > 0) {
                SimpleDateFormat format = new SimpleDateFormat(CStudioConstants.DATE_PATTERN_WORKFLOW);
                List<DmDependencyTO> submittedItems = new FastList<DmDependencyTO>();
                for (int index = 0; index < length; index++) {
                    JSONObject item = items.getJSONObject(index);
                    DmDependencyTO submittedItem = getSubmittedItem(site, item, format, scheduledDate);
                    submittedItems.add(submittedItem);
                }
                List<String> nodeRefs = new FastList<String>();
                for (DmDependencyTO goLiveItem : submittedItems) {
                    String fullPath = servicesConfig.getRepositoryRootPath(site) + goLiveItem.getUri();
                    NodeRef nodeRef = persistenceManagerService.getNodeRef(fullPath);
                    if (nodeRef != null) {
                        nodeRefs.add(nodeRef.getId());
                    }
                }
                persistenceManagerService.setSystemProcessingBulk(nodeRefs, true);
                DmWorkflowService dmWorkflowService = getServicesManager().getService(DmWorkflowService.class);
                dmWorkflowService.reject(site, sub, submittedItems, reason, approver);
                persistenceManagerService.setSystemProcessingBulk(nodeRefs, false);
                result.setSuccess(true);
                NotificationService notificationService = getServicesManager().getService(NotificationService.class);
                result.setMessage(notificationService.getCompleteMessage(site, NotificationService.COMPLETE_REJECT));
            } else {
                result.setSuccess(false);
                result.setMessage("No items provided for preparation.");
            }
        } catch (JSONException e) {
            result.setSuccess(false);
            result.setMessage(e.getMessage());
        }
        return result;
    }

    /**
     * get scheduled items that are approved and awaiting for deployment
     *
     * @param site
     * @param sub
     * @param sort         sort key for categories
     * @param ascending
     * @param subSort      sort key for category items
     * @param subAscending
     * @return scheduled items in JSON
     * @throws ServiceException
     */
    public String getScheduledItems(String site, String sub, String sort, boolean ascending, String subSort, boolean subAscending, String filterType) throws ServiceException {
        if (StringUtils.isEmpty(sort)) {
            sort = DmContentItemComparator.SORT_EVENT_DATE;
        }
        DmContentItemComparator comparator = new DmContentItemComparator(sort, ascending, true, true);
        DmContentItemComparator subComparator = new DmContentItemComparator(subSort, subAscending, true, true);
        DmWorkflowService dmWorkflowService = getServicesManager().getService(DmWorkflowService.class);
        List<DmContentItemTO> items = null;
        items = dmWorkflowService.getScheduledItems(site, sub, comparator, subComparator, filterType);
        JSONObject jsonObject = new JSONObject();
        int total = 0;
        if (items != null) {
            for (DmContentItemTO item : items) {
                total += item.getNumOfChildren();
            }
        }
        jsonObject.put(CStudioConstants.PROPERTY_TOTAL, total);
        jsonObject.put(CStudioConstants.PROPERTY_SORTED_BY, sort);
        jsonObject.put(CStudioConstants.PROPERTY_SORT_ASCENDING, String.valueOf(ascending));
        jsonObject.put(CStudioConstants.PROPERTY_DOCUMENTS, items);
        return jsonObject.toString();
    }

    /**
     * approve workflows and schedule them as specified in the request
     *
     * @param site
     * @param sub
     * @param request
     * @return call result
     * @throws ServiceException
     */
    public ResultTO goDelete(String site, String sub, String request, String user) {
        String md5 = DmUtils.getMd5ForFile(request);
        String id = site + ":" + user + ":" + md5;
        GeneralLockService generalLockService = _servicesManager.getService(GeneralLockService.class);
        if (!generalLockService.tryLock(id)) {
            generalLockService.lock(id);
            generalLockService.unlock(id);
            return new ResultTO();
        }
        try {
            Map<String, String> map = new HashMap<String, String>();
            map.put(CStudioConstants.USER, user);
            ThreadLocalContainer.set(map);
            return approve(site, sub, request, Operation.DELETE);
        } finally {
            ThreadLocalContainer.remove();
            generalLockService.unlock(id);
        }
    }

    /**
     * update all items that are associated with the current task to be
     * scheduled
     *
     * @param packageRef
     */
    public void updateItemStatusToScheduled(NodeRef packageRef, Date date, String desc) {
        DmWorkflowService dmWorkflowService = getServicesManager().getService(DmWorkflowService.class);
        dmWorkflowService.updateItemStatus(packageRef, DmConstants.DM_STATUS_SCHEDULED, date);
    }

    public void scheduleDeleteSubmission(NodeRef packageRef, String workflowId, String description) {
        DmWorkflowService dmWorkflowService = getServicesManager().getService(DmWorkflowService.class);
        dmWorkflowService.scheduleDeleteSubmission(packageRef, workflowId, description);
    }

    public String getWorkflowAffectedPaths(String site, String path) {
        DmWorkflowService dmWorkflowService = getServicesManager().getService(DmWorkflowService.class);
        List<DmContentItemTO> affectedItems = dmWorkflowService.getWorkflowAffectedPaths(site, path);
        JSONObject jsonResponse = prepareAffectedPathsResult(affectedItems,site);
        return jsonResponse.toString();
    }

    private JSONObject prepareAffectedPathsResult(List<DmContentItemTO> items,String site) {
        JSONObject toReturn = new JSONObject();
        JSONArray jsonItems = new JSONArray();
        for (DmContentItemTO item : items) {
            JSONObject jsonItem = new JSONObject();
            jsonItem.put("path", item.getUri());
            jsonItem.put("browserUri", item.getBrowserUri());
            if (StringUtils.isNotEmpty(item.getInternalName())) {
                jsonItem.put("name", item.getInternalName());
            } else {
                jsonItem.put("name", item.getName());
            }
            jsonItems.add(jsonItem);
        }
        toReturn.put("items", jsonItems);
        return toReturn;
    }

    /**
     * delete the submitted items and submit to workflow
     *
     * @param site
     * @param request
     * @throws ServiceException
     * @return call result
     */
    public ResultTO submitToDelete(String site, String request) throws ServiceException {
        return submitForApproval(site, null, request, true);
    }
}
