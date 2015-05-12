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
package org.craftercms.cstudio.publishing.servlet;

import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.FileUploadException;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.apache.commons.io.IOUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.craftercms.cstudio.publishing.PublishedChangeSet;
import org.craftercms.cstudio.publishing.exception.PublishingException;
import org.craftercms.cstudio.publishing.processor.PublishingProcessor;
import org.craftercms.cstudio.publishing.target.PublishingTarget;
import org.craftercms.cstudio.publishing.target.TargetManager;
import org.springframework.util.StringUtils;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.util.*;
import java.util.Map.Entry;

/**
 * @author Dejan Brkic
 */
public class FileUploadServlet extends HttpServlet {

	/**
	 * 
	 */
	private static final long serialVersionUID = 3281574055609732424L;

	private static Log LOGGER = LogFactory.getLog(FileUploadServlet.class);

    public static final String PARAM_PASSWORD = "password";
	public static String PARAM_SITE = "siteId";
    public static String PARAM_TARGET = "target";
	public static String PARAM_DELETED_FILES = "deletedFiles";
	public static String CONFIG_ROOT = "root";
    public static String CONFIG_CONTENT_FOLDER = "contentFolder";
    public static String CONFIG_METADATA_FOLDER = "metadataFolder";
    public static String CONFIG_METADATA_FILENAME_SUFFIX = ".meta.xml";
    public static String CONFIG_MULTI_TENANCY_VARIABLE = "\\{siteId\\}";

	public static String FILES_SEPARATOR = ",";

	protected TargetManager targetManager;

    private String password;
    public String getPassword() {
        return password;
    }
    public void setPassword(String password) {
        this.password = password;
    }

    @Override
	protected void doGet(HttpServletRequest request, HttpServletResponse response) {
		if (ServletFileUpload.isMultipartContent(request)) {
			ServletFileUpload servletFileUpload = createServletFileUpload();
			List<FileItem> fileItemsList = null;
			Map<String, String> parameters = new HashMap<String, String>(11);
			Map<String, InputStream> files = new HashMap<String, InputStream>(11);
			try {
				fileItemsList = servletFileUpload.parseRequest(request);
				for (FileItem fileItem : fileItemsList) {
					if (fileItem.isFormField()) {
						parameters.put(fileItem.getFieldName(), fileItem.getString());
					} else {
						files.put(fileItem.getFieldName(), fileItem.getInputStream());
					}
				}
				
				if (LOGGER.isDebugEnabled()) {
					
					StringBuilder parametersLog = new StringBuilder("Request Parameters : ");
					
					for(Entry<String, String> entry : parameters.entrySet()) {
						
					    String key = entry.getKey();
					    String value = entry.getValue();
					    
					    
					    if(key.equals(PARAM_PASSWORD)){
					    	value = "********";
					    }
					    parametersLog.append(" " + key + " = " + value ); 
					}
					
					LOGGER.debug(parametersLog.toString());
				}
				
                String password = parameters.get(PARAM_PASSWORD);
                if (password != null && password.equalsIgnoreCase(this.password)) {
                    deployFiles(parameters, files);
                    response.setStatus(HttpServletResponse.SC_OK);
                } else {
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    if (LOGGER.isWarnEnabled()) {
                        LOGGER.warn("Illegal publish request received.");
                    }
                }
			} catch (Exception e) {
				handleErrorCase(files, response, e);
			}
		}
	}

	/**
	 * handle error case
	 * 
	 * @param files
	 * @param response
	 * @param exception
	 */
	private void handleErrorCase(Map<String, InputStream> files, HttpServletResponse response, Exception exception) {
		if (LOGGER.isErrorEnabled()) {
			LOGGER.error("Failed to upload files.", exception);
		}
		closeAll(files);
		try {
			response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, exception.getMessage());
		} catch (IOException e) {
			response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
			if (LOGGER.isErrorEnabled()) {
				LOGGER.error("Failed to upload files.", e);
			}
		}
	}

	/**
	 * close all inputstream 
	 * 
	 * @param files
	 */
	private void closeAll(Map<String, InputStream> files) {
		if (files != null) {
			for (String file : files.keySet()) {
				InputStream steam = files.get(file);
				IOUtils.closeQuietly(steam);
				
			}
		}
	}

	/*
	 * (non-Javadoc)
	 * @see javax.servlet.http.HttpServlet#doPost(javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse)
	 */
	@Override
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException,
			IOException {
		doGet(request, response);
	}

	/**
	 * create servlet file upload 
	 * 
	 * @return
	 */
	protected ServletFileUpload createServletFileUpload() {
		DiskFileItemFactory diskFileItemFactory = new DiskFileItemFactory();
        String tempPath = System.getProperty("java.io.tmpdir");
        if (tempPath == null) {
            tempPath = "temp";
        }
		File repoPath =  new File(tempPath + File.separator + "crafter");
        if (!repoPath.exists()) {
            repoPath.mkdirs();
        }
		diskFileItemFactory.setRepository(repoPath);
		ServletFileUpload toRet = new ServletFileUpload(diskFileItemFactory);
		return toRet;
	}

	/**
	 * write files to targets
	 * 
	 * @param parameters
	 * @param files
	 * @throws IOException
	 */
	protected void deployFiles(Map<String, String> parameters, Map<String, InputStream> files) throws IOException {
		String paramTarget = parameters.get(PARAM_TARGET);
		PublishingTarget target = this.targetManager.getTarget(paramTarget);
		if (target != null) {
			PublishedChangeSet changeSet = new PublishedChangeSet();
			// TODO: support pre-processors in future
			writeToTarget(parameters, files, target, changeSet);
			deleteFromTarget(parameters, target, changeSet);
			// run through post processors
			if (target.isDefaultProcessingEnabled()) {
				try {
					target.getDefaultPostProcessor().doProcess(changeSet, parameters, target);
				} catch (PublishingException e) {
					LOGGER.error("Error while running a default post processor", e);
				}
			}
			doPostProcessing(changeSet, parameters, target);
		} else {
			throw new IOException("No configuration exists for " + paramTarget);
		}
			
	}
	
	/**
	 * create or update files to target 
	 * 
	 * @param parameters
	 * @param files
	 * @param target
	 * @param changeSet 
	 * @throws IOException 
	 */
	protected void writeToTarget(Map<String, String> parameters, Map<String, InputStream> files, 
			PublishingTarget target, PublishedChangeSet changeSet) throws IOException {
		List<String> createdFiles = new ArrayList<String>(files.size());
		List<String> updatedFiles = new ArrayList<String>(files.size());
        String site = parameters.get(PARAM_SITE);
		// write files to the target path
		for (Map.Entry<String, InputStream> entry : files.entrySet()) {
			String locationParamName = entry.getKey().replace("File", "Location").replace("metadata", "content");
            boolean isMetadata = entry.getKey().startsWith("metadata");
			String contentLocation = parameters.get(locationParamName);
            StringBuilder sbFullPath = new StringBuilder(target.getParameter(CONFIG_ROOT));
            sbFullPath.append(File.separator);
            if (isMetadata)
                sbFullPath.append(target.getParameter(CONFIG_METADATA_FOLDER));
            else
                sbFullPath.append(target.getParameter(CONFIG_CONTENT_FOLDER));
            sbFullPath.append(contentLocation);
            if (isMetadata) {
                sbFullPath.append(CONFIG_METADATA_FILENAME_SUFFIX);
            }
			//String fullPath = target.getParameter(CONFIG_ROOT) + "/" + contentLocation;
			if (LOGGER.isInfoEnabled()) {
				LOGGER.info("writing " + sbFullPath.toString());
			}
            String fullPath = sbFullPath.toString();
            if (StringUtils.hasText(site)) {
                fullPath = fullPath.replaceAll(CONFIG_MULTI_TENANCY_VARIABLE, site);
            }
            File file = new File(fullPath);
			OutputStream outputStream = null;
			try {
				// create new file if doesn't exist
				boolean created = false;
				if (!file.exists()) {
					file.getParentFile().mkdirs();
					file.createNewFile();
					created = true;
				}
				outputStream = new FileOutputStream(file);
				IOUtils.copy(entry.getValue(), outputStream);
                if (!isMetadata) {
                    if (created) {
                        createdFiles.add(contentLocation);
                    } else {
                        updatedFiles.add(contentLocation);
                    }
                }
			} catch (FileNotFoundException e) {
				if (LOGGER.isErrorEnabled()) { 
					LOGGER.error("Error: not able to open output stream for file " + contentLocation + " for " + target.getName());
				}
				throw e;
			} catch (IOException e) {
				if (LOGGER.isErrorEnabled()) {
					LOGGER.error("Error: not able to write file " + contentLocation + " for " + target.getName());
				}
				throw e;
			} finally {
				IOUtils.closeQuietly(entry.getValue());
				IOUtils.closeQuietly(outputStream);
			}
		}
		changeSet.setCreatedFiles(createdFiles);
		changeSet.setUpdatedFiles(updatedFiles);
	}

	/**
	 * delete files form target
	 * @param parameters
	 * @param target
	 * @param changeSet
	 */
	protected void deleteFromTarget(Map<String, String> parameters, PublishingTarget target, PublishedChangeSet changeSet) {
		String deletedList = parameters.get(PARAM_DELETED_FILES);
        String site = parameters.get(PARAM_SITE);
		if (deletedList != null) {
			StringTokenizer tokens = new StringTokenizer(deletedList, FILES_SEPARATOR);
			List<String> deletedFiles = new ArrayList<String>(tokens.countTokens());
			while (tokens.hasMoreElements()) {
				String contentLocation = tokens.nextToken();
				contentLocation = StringUtils.trimWhitespace(contentLocation);
				String root = target.getParameter(CONFIG_ROOT);
				String fullPath = root + File.separator + target.getParameter(CONFIG_CONTENT_FOLDER) + contentLocation;
				if (LOGGER.isInfoEnabled()) {
					LOGGER.info("deleting " + fullPath);
				}
                if (StringUtils.hasText(site)) {
                    fullPath = fullPath.replaceAll(CONFIG_MULTI_TENANCY_VARIABLE, site);
                }
				File file = new File(fullPath);
				if (file.exists()) {
					if (file.isFile()) {
						file.delete();
						deletedFiles.add(contentLocation);
					} else {
						deleteChildren(file.list(), fullPath, contentLocation, deletedFiles);
						file.delete();
					}
				} else {
					if (LOGGER.isDebugEnabled()) {
						LOGGER.debug(fullPath + " is not deleted since it does not exsit.");
					}
				}
				fullPath = root + '/' + target.getParameter(CONFIG_METADATA_FOLDER) +
				           contentLocation + CONFIG_METADATA_FILENAME_SUFFIX;
				if (LOGGER.isDebugEnabled()) {
					LOGGER.debug("deleting " + fullPath);
				}
                if (StringUtils.hasText(site)) {
                    fullPath = fullPath.replaceAll(CONFIG_MULTI_TENANCY_VARIABLE, site);
                }
				file = new File(fullPath);
				if (file.exists()) {
					file.delete();
				}
				else if (LOGGER.isDebugEnabled()) {
					LOGGER.debug(fullPath + " is not deleted since it does not exsit.");
				}
			}
			changeSet.setDeletedFiles(deletedFiles);
		} 
	}

	/**
	 * delete child items
	 * 
	 * @param children
	 * @param parentPath
	 * @param deletedFiles
	 */
	private void deleteChildren(String[] children, String parentFullPath, String parentPath, List<String> deletedFiles) {
		if (children != null) {
			for (String child : children) {
				String childFullPath = parentFullPath + File.separator + child;
				String childPath = parentPath + File.separator + child;
				File file = new File(childFullPath);
				if (file.isFile()) {
					file.delete();
					deletedFiles.add(childPath);
				} else {
					deleteChildren(file.list(), childFullPath, childPath, deletedFiles);
					file.delete();
				}
			}
		}
	}

	/**
	 * run published files through the post processors
	 * 
	 * @param changeSet
	 * @param parameters 
	 * @param target
	 */
	protected void doPostProcessing(PublishedChangeSet changeSet, Map<String, String> parameters, PublishingTarget target) {
		List<PublishingProcessor> postProcessors = target.getPostProcessors();
		if (postProcessors != null) {
			try {
				for (PublishingProcessor processor : postProcessors) {
					if (LOGGER.isDebugEnabled()) {
						LOGGER.debug("Running files through " + processor.getName());
					}
					processor.doProcess(changeSet, parameters, target);
				}
			} catch (Exception e) {
                LOGGER.error("Error while running a post processor", e);
			}
		}
	}

	/**
	 * @return the targetManager
	 */
	public TargetManager getTargetManager() {
		return targetManager;
	}

	/**
	 * @param targetManager the targetManager to set
	 */
	public void setTargetManager(TargetManager targetManager) {
		this.targetManager = targetManager;
	}

}
