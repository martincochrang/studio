package org.craftercms.cstudio.publishing.processor;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.craftercms.cstudio.publishing.PublishedChangeSet;
import org.craftercms.cstudio.publishing.exception.PublishingException;
import org.craftercms.cstudio.publishing.servlet.FileUploadServlet;
import org.craftercms.cstudio.publishing.target.PublishingTarget;
import org.craftercms.search.service.SearchService;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

public class SearchIndexBinaryFilesProcessor implements PublishingProcessor {

    private static final Log logger = LogFactory.getLog(SearchAttachmentProcessor.class);

    private String siteName;
    private SearchService searchService;

    /**
     * set a sitename to override in index
     *
     * @param siteName
     *          an override siteName in index
     */
    public void setSiteName(String siteName) {
        if (!StringUtils.isEmpty(siteName)) {
            // check if it is preview for backward compatibility
            if (!SITE_NAME_PREVIEW.equalsIgnoreCase(siteName)) {
                if (logger.isDebugEnabled()) logger.debug("Overriding site name in index with " + siteName);
                this.siteName = siteName;
            }
        }
    }

    public void setSearchService(SearchService searchService) { this.searchService = searchService; }

    @Override
    public void doProcess(PublishedChangeSet changeSet, Map<String, String> parameters, PublishingTarget target) throws PublishingException {
        String root = target.getParameter(FileUploadServlet.CONFIG_ROOT);
        String contentFolder = target.getParameter(FileUploadServlet.CONFIG_CONTENT_FOLDER);
        String siteId = (!StringUtils.isEmpty(siteName)) ? siteName : parameters.get(FileUploadServlet.PARAM_SITE);

        root += "/" + contentFolder;
        if (org.springframework.util.StringUtils.hasText(siteId)) {
            root = root.replaceAll(FileUploadServlet.CONFIG_MULTI_TENANCY_VARIABLE, siteId);
        }

        List<String> createdFiles = changeSet.getCreatedFiles();
        List<String> updatedFiles = changeSet.getUpdatedFiles();
        List<String> deletedFiles = changeSet.getDeletedFiles();

        try {
            if (CollectionUtils.isNotEmpty(createdFiles)) {
                update(siteId, root, createdFiles, false);
            }
            if (CollectionUtils.isNotEmpty(updatedFiles)) {
                update(siteId, root, updatedFiles, false);
            }
            if (CollectionUtils.isNotEmpty(deletedFiles)) {
                update(siteId, root, deletedFiles, true);
            }
        } catch (Exception exc) {
            int x = 0;
        }
    }

    private void update(String siteId, String root, List<String> fileList, boolean isDelete)
            throws IOException {
        for (String fileName : fileList) {
            File file = new File(root + fileName);
            if (isDelete) {
                searchService.delete(siteId, fileName);
            } else {
                searchService.updateDocument(siteId, fileName, file);
            }
        }
    }

    @Override
    public String getName() {
        return SearchIndexBinaryFilesProcessor.class.getSimpleName();
    }
}
