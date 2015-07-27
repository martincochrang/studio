package org.craftercms.cstudio.publishing.processor;

import org.apache.commons.io.IOUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.craftercms.cstudio.publishing.PublishedChangeSet;
import org.craftercms.cstudio.publishing.servlet.FileUploadServlet;
import org.craftercms.cstudio.publishing.target.PublishingTarget;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;

/**
 * <p>Windows Command Processor</p>
 */
public class CommandProcessor implements PublishingProcessor {

    private static Log LOGGER = LogFactory.getLog(CommandProcessor.class);

    final static String STATUS_CREATED = "CREATED";
    final static String STATUS_UPDATED = "UPDATED";
    final static String STATUS_DELETED = "DELETED";

    // command to run
    private String command;

    // file path patterns to match for execution
    private List<String> matchPatterns;

    @Override
    public void doProcess(PublishedChangeSet changeSet,
                          Map<String, String> parameters, PublishingTarget target) {
        String root = target.getParameter(FileUploadServlet.CONFIG_ROOT);
        String contentFolder = target.getParameter(FileUploadServlet.CONFIG_CONTENT_FOLDER);
        root += "/" + contentFolder;
        String siteId = parameters.get(FileUploadServlet.PARAM_SITE);

        processFiles(siteId, root, changeSet.getCreatedFiles(), STATUS_CREATED);
        processFiles(siteId, root, changeSet.getUpdatedFiles(), STATUS_UPDATED);
        processFiles(siteId, root, changeSet.getDeletedFiles(), STATUS_DELETED);
    }

    /**
     * <p>process files</p>
     *
     * @param site
     * @param root
     * @param files
     * @param status
     */
    private void processFiles(String site, String root, List<String> files, String status) {
        if (files != null) {
            for (String file : files) {
                if (isMatchingPattern(file)) {
                    if (LOGGER.isDebugEnabled()) {
                        LOGGER.debug("Processing " + file);
                    }
                    processFile(site, root, file, status);
                } else {
                    if (LOGGER.isDebugEnabled()) {
                        LOGGER.debug(file + " does not match a pattern.");
                    }
                }
            }
        }
    }

    /**
     * <p>process a single file</p>
     *
     * @param site
     * @param root
     * @param file
     * @param status
     */
    private void processFile(String site, String root, String file, String status) {
        BufferedReader reader = null;
        try {
            String fileCommand = command.replaceAll("SITE", site).replaceAll("ROOT", root)
                    .replaceAll("FILE", file).replaceAll("STATUS", status);
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Running command with ProcessBuilder: " + fileCommand);
            }
            StringTokenizer tokenizer = new StringTokenizer(fileCommand, " ");
            List<String> processCommand = new ArrayList<String>();
            while (tokenizer.hasMoreTokens()) {
                processCommand.add(tokenizer.nextToken());
            }
            ProcessBuilder pb = new ProcessBuilder(processCommand);
            pb.redirectErrorStream(true);
            Process process = pb.start();
            String line;
            reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()));
            if (LOGGER.isDebugEnabled()) {
                while ((line = reader.readLine()) != null) {
                    LOGGER.debug(line);
                }
            }
            process.waitFor();
        } catch (IOException e) {
            LOGGER.error("Error while processing " + file, e);
        } catch (InterruptedException e) {
            LOGGER.error("Error while processing " + file, e);
        } finally {
            if (reader != null)
                IOUtils.closeQuietly(reader);
        }

    }

    /**
     * check if the file path is matching one of patterns
     *
     * @param file
     * @return true if matching or no match pattern is set
     */
    protected boolean isMatchingPattern(String file) {
        if (getMatchPatterns() != null) {
            for (String matchPattern : getMatchPatterns()) {
                if (file.matches(matchPattern)) {
                    System.out.println(file + " matched " + matchPattern);
                    return true;
                } else {
                    System.out.println(file + " didn't match " + matchPattern);
                }
            }
        }
        return true;
    }

    @Override
    public String getName() {
        return "CommandProcessor";
    }

    /**
     * <p>set command</p>
     *
     * @param command
     */
    public void setCommand(String command) {
        this.command = command;
    }

    /**
     * <p>get match patterns</p>
     *
     * @return match patterns
     */
    public List<String> getMatchPatterns() { return this.matchPatterns; }

    /**
     * <p>set match patterns</p>
     *
     * @param matchPatterns
     */
    public void setMatchPatterns(List<String> matchPatterns) { this.matchPatterns = matchPatterns; }

}
