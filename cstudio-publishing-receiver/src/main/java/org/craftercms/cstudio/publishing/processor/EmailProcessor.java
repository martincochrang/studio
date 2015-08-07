package org.craftercms.cstudio.publishing.processor;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.craftercms.cstudio.publishing.PublishedChangeSet;
import org.craftercms.cstudio.publishing.servlet.FileUploadServlet;
import org.craftercms.cstudio.publishing.target.PublishingTarget;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.dom4j.Node;
import org.dom4j.io.SAXReader;

import javax.mail.*;
import javax.mail.internet.*;
import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.craftercms.cstudio.publishing.exception.PublishingException;

/**
 * <p>A post processor that sends email with the content published</p>
 */
public class EmailProcessor implements PublishingProcessor {

    private static Log LOGGER = LogFactory.getLog(EmailProcessor.class);

    // TODO: we might move this to configuration
    private static String PAGE_ROOT = "/site/website";
    private static String PROTOCOL_SMTPS = "smtps";
    private static String PROTOCOL_TLS= "tls";

    private static String MAIL_SMTP_AUTH = "mail.smtp.auth";
    private static String MAIL_SMTP_HOST = "mail.smtp.host";
    private static String MAIL_SMTP_PORT = "mail.smtp.port";
    private static String MAIL_SMTP_SSL = "mail.smtp.ssl.enable";
    private static String MAIL_SMTP_STARTTLS = "mail.smtp.starttls.enable";

    public static String CHAR_ENCODING = "UTF-8";

    // mail server properties
    private String host;
    private String port;
    private String username;
    private String password;
    // mail server protocol
    private String protocol;
    // mail server authentication properties
    private boolean authenticated;

    // default from email address if no value from mailFromPath
    private String defaultMailFrom;

    // mail rendering site information
    private String previewUrl;
    private String componentController;
    private String siteName;
    // request headers to add when accessing the preview server
    private Map<String, String> headers;

    // file path patterns to match for sending email
    private List<String> matchPatterns;
    private List<String> positiveChecks;
    // a list of string patterns must not be found in content
    private List<String> negativeChecks;

    // email property xpath to lookup
    private String mailToPath;
    private String mailCcPath;
    private String mailBccPath;
    private String mailFromPath;
    private String replyToPath;
    private String titlePath;
    private String textContentPath;
    private String sendEmailFlagPath;

    // internal image path pattern
    private String imagePattern;

    // url replacement
    private Map<String, String> urlReplacements;

    // content replacement
    private Map<String, String> contentReplacements;


    @Override
    public String getName() {
        return "EmailProcessor";
    }

    @Override
    public void doProcess(PublishedChangeSet changeSet,
                          Map<String, String> parameters, PublishingTarget target) throws PublishingException {
        String root = target.getParameter(FileUploadServlet.CONFIG_ROOT);
        String contentFolder = target.getParameter(FileUploadServlet.CONFIG_CONTENT_FOLDER);
        root += "/" + contentFolder;
        String siteId = StringUtils.isEmpty(getSiteName()) ? parameters.get(FileUploadServlet.PARAM_SITE) : getSiteName();
        processFiles(siteId, root, changeSet.getCreatedFiles());
        processFiles(siteId, root, changeSet.getUpdatedFiles());
    }

    /**
     * <p>process files</p>
     *
     * @param site
     * @param root
     * @param files
     */
    protected void processFiles(String site, String root, List<String> files) {
        if (files != null) {
            for (String file : files) {
                if (isMatchingPattern(file)) {
                    if (LOGGER.isDebugEnabled()) {
                        LOGGER.debug("Processing " + file);
                    }
                    try {
                        processFile(site, root, file);
                    } catch (PublishingException e) {
                        // if fails to send an email, continue to process next item
                        LOGGER.error("Error while processing " + file, e);
                    }
                } else {
                    if (LOGGER.isDebugEnabled()) {
                        LOGGER.debug(file + " does not match a pattern.");
                    }
                }
            }
        }
    }
    /**
     * process each file updated or created
     *
     * @param site crafter site
     * @param root root path
     * @param file file path
     * @throws PublishingException
     */
    protected void processFile(String site, String root, String file)
            throws PublishingException {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Processing " + file);
        }
        try {
            SAXReader reader = new SAXReader();
            reader.setEncoding(getCharEncoding());
            Document document = reader.read(root + file);
            Element rootEl = document.getRootElement();
            String sendEmailFlag = rootEl.valueOf(getSendEmailFlagPath());
            // check if sendEmailFlag is set. by default it will send an email
            if (StringUtils.isEmpty(sendEmailFlag) || sendEmailFlag.equalsIgnoreCase("true")) {
                // add recipients
                List<Address> mailToList = getEmailList(rootEl, getMailToPath());
                List<Address> mailCcList = getEmailList(rootEl, getMailCcPath());
                List<Address> mailBccList = getEmailList(rootEl, getMailBccPath());

                if (CollectionUtils.isEmpty(mailToList) && CollectionUtils.isEmpty(mailCcList)
                        && CollectionUtils.isEmpty(mailBccList)) {
                    if (LOGGER.isDebugEnabled()) {
                        LOGGER.debug("No recipient is set. Skipping.");
                    }
                } else {
                    String mailFromStr = rootEl.valueOf(getMailFromPath());
                    if (StringUtils.isEmpty(mailFromStr)) {
                        mailFromStr = getDefaultMailFrom();
                    }
                    Address mailFromAddress = this.createEmailAddress(mailFromStr);
                    // add reply to list
                    List<Address> replyToList = getEmailList(rootEl, getReplyToPath());
                    String title = rootEl.valueOf(getTitlePath());
                    String textContent = rootEl.valueOf(getTextContentPath());
                    String htmlContent = getHtmlContent(file, site);
                    sendEmail(root, mailFromAddress, replyToList, mailToList, mailCcList, mailBccList,
                            title, htmlContent, textContent);
                }
            } else {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("sendEmailFlag: " + sendEmailFlag);
                }
            }
        } catch (IOException e) {
            throw new PublishingException(e);
        } catch (DocumentException e) {
            throw new PublishingException(e);
        }
    }

    /**
     * <p>get a list of email addresses from content</p>
     *
     * @param parentEl
     * @param path
     */
    protected List<Address> getEmailList(Element parentEl, String path) {
        List<Address> emailList = null;
        // email addresses as repeating elements
        if (path.contains("//")) {
            List<Node> mailNodes = parentEl.selectNodes(path);
            if (!CollectionUtils.isEmpty(mailNodes)) {
                emailList = new ArrayList<>();
                for (Node mailNode : mailNodes) {
                    Address address = createEmailAddress(mailNode.getText());
                    if (address != null) {
                        emailList.add(address);
                    }
                }
            }
        } else {
            // email addresses as a semicolon-separated list
            String listStr = parentEl.valueOf(path);
            if (!StringUtils.isEmpty(listStr)) {
                emailList = new ArrayList<>();
                StringTokenizer tokenizer = new StringTokenizer(listStr, ";");
                while (tokenizer.hasMoreTokens()) {
                    Address address = createEmailAddress(tokenizer.nextToken());
                    if (address != null) {
                        emailList.add(address);
                    }
                }
            }
        }
        return emailList;
    }

    /**
     * <p>create an email address from the string given</p>
     *
     * @param emailStr email address or name & email address format
     * @return email address
     */
    protected Address createEmailAddress(String emailStr) {
        if (!StringUtils.isEmpty(emailStr)) {
            String email = null;
            String name = null;
            int index = emailStr.indexOf("<");
            if (index > 0 && index < (emailStr.length() - 1)) {
                // expecting John Smith <johnsemail@hisserver.com> format here
                name = emailStr.substring(0, index).trim();
                email = emailStr.substring(index + 1, emailStr.length() - 1).trim();
            } else {
                // email address only
                email = emailStr.trim();
            }
            if (email.length() > 0) {
                try {
                    Address address = (StringUtils.isEmpty(name))
                            ? new InternetAddress(email) : new InternetAddress(email, name, getCharEncoding());
                    return address;
                } catch (AddressException e) {
                    if (LOGGER.isDebugEnabled()) {
                        LOGGER.debug(email + " is not a valid email address.");
                    }
                } catch (UnsupportedEncodingException e) {
                    if (LOGGER.isDebugEnabled()) {
                        LOGGER.debug("Error while creating an email address.", e);
                    }
                }
            }
        }
        return null;
    }

    /**
     * send an email with the properties given
     *
     * @param root site root
     * @param mailFrom from address
     * @param replyToList reply to address
     * @param mailToList to addresses
     * @param mailCcList cc addresses
     * @param mailBccList bcc addresses
     * @param title mail title
     * @param htmlContent HTML content
     * @param textContent text content
     * @throws PublishingException
     */
    protected void sendEmail(String root, Address mailFrom, List<Address> replyToList,
                             List<Address> mailToList, List<Address> mailCcList, List<Address> mailBccList,
                             String title, String htmlContent, String textContent)
            throws PublishingException {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Sending email from: " + mailFrom);
            LOGGER.debug("to: " + mailToList + ", cc: " + mailCcList + ", bcc: " + mailBccList);
            LOGGER.debug("reply to: " + replyToList);
            LOGGER.debug("title: " + title);
            LOGGER.debug("text content: " + textContent);
        }
        try {
            Session session = createSession();
            // create email body
            Multipart multipart = new MimeMultipart("alternative");
            MimeBodyPart textPart = new MimeBodyPart();
            textPart.setText(textContent);
            multipart.addBodyPart(textPart);
            MimeBodyPart htmlPart = new MimeBodyPart();
            htmlContent = embedImages(root, multipart, htmlContent);
            htmlPart.setContent(htmlContent, "text/html");
            multipart.addBodyPart(htmlPart);

            Message msg = new MimeMessage(session);
            msg.setFrom(mailFrom);
            if (!CollectionUtils.isEmpty(mailToList)) {
                msg.addRecipients(Message.RecipientType.TO, mailToList.toArray(new Address[0]));
            }
            if (!CollectionUtils.isEmpty(mailCcList)) {
                msg.addRecipients(Message.RecipientType.CC, mailCcList.toArray(new Address[0]));
            }
            if (!CollectionUtils.isEmpty(mailBccList)) {
                msg.addRecipients(Message.RecipientType.BCC, mailBccList.toArray(new Address[0]));
            }
            if (!CollectionUtils.isEmpty(replyToList)) {
                msg.setReplyTo(replyToList.toArray(new Address[0]));
            }
            msg.setSubject(title);
            msg.setContent(multipart);
            Transport.send(msg);
        } catch (AddressException e) {
            throw new PublishingException("Error sending email: " + e);
        } catch (MessagingException e) {
            throw new PublishingException("Error sending email: " + e);
        } catch (IOException e) {
            throw new PublishingException("Error sending email: " + e);
        }

    }

    /**
     * <p>create email session</p>
     *
     * @return session
     */
    protected Session createSession() {
        Properties props = createMailProperties();
        Authenticator authenticator = createAuthenticator(props, this.username, this.password);
        return Session.getDefaultInstance(props, authenticator);
    }

    /**
     * embed images
     *
     * @param root site root
     * @param multipart email content holder
     * @param content email content
     * @return email content with embedded images
     * @throws IOException
     * @throws MessagingException
     */
    protected String embedImages(String root, Multipart multipart, String content) throws IOException, MessagingException {
        Pattern pattern = Pattern.compile("src=\"(" + getImagePattern() + ")\"");
        Matcher matcher = pattern.matcher(content);
        StringBuffer sb = new StringBuffer();
        Map<String, String> cidMapping = new HashMap<String, String>();
        while (matcher.find()) {
            String imagePath = matcher.group(1);
            // check if the image is already added
            String cid = cidMapping.get(imagePath);
            if (cid == null) {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("Attaching image: " + root + imagePath);
                }
                cid = UUID.randomUUID().toString();
                // get the image and add
                cidMapping.put(imagePath, cid);
                MimeBodyPart imagePart = new MimeBodyPart();
                File imageFile = new File(root + imagePath);
                if (imageFile.exists()) {
                    imagePart.attachFile(imageFile);
                    imagePart.setContentID("<" + cid + ">");
                    imagePart.setDisposition(MimeBodyPart.INLINE);
                    multipart.addBodyPart(imagePart);
                } else {
                    if (LOGGER.isDebugEnabled()) {
                        LOGGER.debug(root + imagePath + " does not exist.");
                    }
                }
            }
            matcher.appendReplacement(sb, "src=\"cid:" + cid + "\"");
        }
        matcher.appendTail(sb);
        return sb.toString();
    }



    /**
     * get html content from preview site
     * @param path content path
     * @param siteName crafter site name
     * @return HTML page content
     * @throws IOException
     * @throws PublishingException
     */
    protected String getHtmlContent(String path, String siteName) throws IOException, PublishingException {

        String previewUrl = createPreviewUrl(path, siteName);
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Accessing " + previewUrl);
        }
        HttpClient client = new HttpClient();
        HttpMethod method = new GetMethod(previewUrl);
        if (getHeaders() != null) {
            for (Map.Entry<String, String> entry : getHeaders().entrySet()) {
                method.addRequestHeader(entry.getKey(), entry.getValue());
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("Adding " + entry.getKey() + ", " + entry.getValue() + " to the request");
                }
            }
        }
        int status = client.executeMethod(method);
        if (status == HttpStatus.SC_OK) {
            String htmlContent = method.getResponseBodyAsString();
            if (getContentReplacements() != null) {
                for (Map.Entry<String, String> entry : getContentReplacements().entrySet()) {
                    htmlContent = htmlContent.replaceAll(entry.getKey(), entry.getValue());
                }
            }
            // check the content to make sure it's valid
            validateContent(htmlContent);
            return htmlContent;
        } else {
            throw new PublishingException("STATUS:" + status + "\n" + method.getResponseBodyAsString());
        }

    }

    /**
     * <p>create a preview url</p>
     *
     * @param path content path
     * @param siteName crafter site id
     * @return the target preview url
     */
    protected String createPreviewUrl(String path, String siteName) {
        if (path.startsWith(PAGE_ROOT)) {
            String contentUrl = path;
            if (getUrlReplacements() != null) {
                for (Map.Entry<String, String> entry : getUrlReplacements().entrySet()) {
                    String replacement = getUrlReplacements().get(entry.getKey());
                    if (LOGGER.isDebugEnabled()) {
                        LOGGER.debug("replacment: " + entry.getKey() + ", " + replacement);
                    }
                    contentUrl = contentUrl.replaceAll(entry.getKey(), replacement);
                }
            }
            return getPreviewUrl() + contentUrl + "?crafterSite=" + siteName;
        } else {
            return getPreviewUrl() + getComponentController() + "?path=" + path + "&crafterSite=" + siteName;
        }
    }

    /**
     * <p>validate content</p>
     *
     * @param htmlContent
     * @throws PublishingException
     */
    protected void validateContent(String htmlContent) throws PublishingException {
        if (getPositiveChecks() != null) {
            for (String positiveCheck : getPositiveChecks()) {
                if (!htmlContent.contains(positiveCheck)) {
                    throw new PublishingException("check failed due to no positive match: "
                            + positiveCheck + "\n" + htmlContent);
                }
            }
        }
        if (getNegativeChecks() != null) {
            for (String negativeCheck : getNegativeChecks()) {
                if (htmlContent.contains(negativeCheck)) {
                    throw new PublishingException("check failed due to a negative match: "
                            + negativeCheck + "\n" + htmlContent);
                }
            }
        }
    }

    /**
     * <p>Create mail server authenticator</p>
     *
     * @param props Mail Properties
     * @param username
     * @param password
     * @return authenticator
     */
    protected Authenticator createAuthenticator(Properties props, final String username, final String password) {
        Authenticator authenticator = null;
        if (isAuthenticated()) {
            props.put(MAIL_SMTP_AUTH, "true");
            authenticator = new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(username, password);
                }
            };
        }
        return authenticator;
    }

    /**
     * <p>Create mail properties</p>
     *
     * @return properties
     */
    protected Properties createMailProperties() {
        Properties props = new Properties();
        props.put(MAIL_SMTP_HOST, getHost());
        props.put(MAIL_SMTP_PORT, getPort());
        if (PROTOCOL_SMTPS.equalsIgnoreCase(getProtocol())) {
            props.put(MAIL_SMTP_SSL, "true");
        } else if (PROTOCOL_TLS.equalsIgnoreCase(getProtocol())) {
            props.put(MAIL_SMTP_STARTTLS, "true");
        }
        return props;
    }



    /**
     * check if the file path is matching one of patterns
     *
     * @param file
     * @return true if matching
     */
    protected boolean isMatchingPattern(String file) {
        if (getMatchPatterns() != null) {
            for (String matchPattern : getMatchPatterns()) {
                if (file.matches(matchPattern)) {
                    if (LOGGER.isDebugEnabled()) {
                        LOGGER.debug(file + " matched " + matchPattern);
                    }
                    return true;
                } else {
                    if (LOGGER.isDebugEnabled()) {
                        LOGGER.debug(file + " didn't match " + matchPattern);
                    }
                }
            }
        }
        // we don't want to send an email for all contents by default
        return false;
    }

    /**
     * <p>get charEncoding</p>
     *
     * @return charEncoding
     */
    public String getCharEncoding() { return EmailProcessor.CHAR_ENCODING; }

    /**
     * <p>get previewUrl</p>
     *
     * @return previewUrl
     */
    public String getPreviewUrl() { return this.previewUrl; }

    /**
     * <p>set previewUrl</p>
     *
     * @param previewUrl
     */
    public void setPreviewUrl(String previewUrl) { this.previewUrl = previewUrl; }

    /**
     * <p>get negativeChecks</p>
     *
     * @return negativeChecks
     */
    public List<String> getNegativeChecks() { return this.negativeChecks; }

    /**
     * <p>set a list of negative check strings</p>
     *
     * @param negativeChecks
     */
    public void setNegativeChecks(List<String> negativeChecks) { this.negativeChecks = negativeChecks; }


    /**
     * <p>get positiveChecks</p>
     *
     * @return positiveChecks
     */
    public List<String> getPositiveChecks() { return this.positiveChecks; }

    /**
     * <p>set a list of positive check string patterns</p>
     *
     * @param positiveChecks
     */
    public void setPositiveChecks(List<String> positiveChecks) { this.positiveChecks = positiveChecks; }

    /**
     * <p>get mailToPath</p>
     *
     * @return mailToPath
     */
    public String getMailToPath() { return this.mailToPath; }

    /**
     * <p>set a mailToPath value</p>
     *
     * @param mailToPath
     */
    public void setMailToPath(String mailToPath) { this.mailToPath = mailToPath; }

    /**
     * <p>get mailCcPath</p>
     *
     * @return mailCcPath
     */
    public String getMailCcPath() { return this.mailCcPath; }

    /**
     * <p>set a mailCcPath value</p>
     *
     * @param mailCcPath
     */
    public void setMailCcPath(String mailCcPath) { this.mailCcPath = mailCcPath; }

    /**
     * <p>get mailBccPath</p>
     *
     * @return mailBccPath
     */
    public String getMailBccPath() { return this.mailBccPath; }

    /**
     * <p>set a mailBccPath value</p>
     *
     * @param mailBccPath
     */
    public void setMailBccPath(String mailBccPath) { this.mailBccPath = mailBccPath; }

    /**
     * <p>get mailFromPath</p>
     *
     * @return mailFromPath
     */
    public String getMailFromPath() { return this.mailFromPath; }

    /**
     * <p>set a mailFromPath value</p>
     *
     * @param mailFromPath
     */
    public void setMailFromPath(String mailFromPath) { this.mailFromPath = mailFromPath; }

    /**
     * <p>get replyToPath</p>
     *
     * @return replyToPath
     */
    public String getReplyToPath() { return this.replyToPath; }

    /**
     * <p>set a replyToPath value</p>
     *
     * @param replyToPath
     */
    public void setReplyToPath(String replyToPath) { this.replyToPath = replyToPath; }

    /**
     * <p>get titlePath</p>
     *
     * @return titlePath
     */
    public String getTitlePath() { return this.titlePath; }

    /**
     * <p>set a titlePath value</p>
     *
     * @param titlePath
     */
    public void setTitlePath(String titlePath) { this.titlePath = titlePath; }

    /**
     * <p>get textContentPath</p>
     *
     * @return textContentPath
     */
    public String getTextContentPath() { return this.textContentPath; }

    /**
     * <p>set a textContentPath value</p>
     *
     * @param textContentPath
     */
    public void setTextContentPath(String textContentPath) { this.textContentPath = textContentPath; }

    /**
     * <p>get defaultMailFrom</p>
     *
     * @return defaultMailFrom
     */
    public String getDefaultMailFrom() { return this.defaultMailFrom; }

    /**
     * <p>set a defaultMailFrom value</p>
     *
     * @param defaultMailFrom
     */
    public void setDefaultMailFrom(String defaultMailFrom) { this.defaultMailFrom = defaultMailFrom; }

    /**
     * <p>get headers</p>
     *
     * @return headers
     */
    public Map<String, String> getHeaders() { return this.headers; }

    /**
     * <p>set request headers</p>
     *
     * @param headers
     */
    public void setHeaders(Map<String, String> headers) {
        this.headers = headers;
    }

    /**
     * <p>get imagePattern</p>
     *
     * @return imagePattern
     */
    public String getImagePattern() { return this.imagePattern; }

    /**
     * <p>set image pattern</p>
     *
     * @param imagePattern
     */
    public void setImagePattern(String imagePattern) { this.imagePattern = imagePattern; }

    /**
     * <p>get componentController</p>
     *
     * @return componentController
     */
    public String getComponentController() { return this.componentController; }

    /**
     * <p>set the component controller path</p>
     *
     * @param componentController
     */
    public void setComponentController(String componentController) { this.componentController = componentController; }

    /**
     * <p>get urlReplacements</p>
     *
     * @return urlReplacements
     */
    public Map<String, String> getUrlReplacements() { return this.urlReplacements; }

    /**
     * <p>set urlReplacement mapping</p>
     *
     * @param urlReplacements
     */
    public void setUrlReplacements(Map<String, String> urlReplacements) { this.urlReplacements = urlReplacements; }

    /**
     * <p>get contentReplacements</p>
     *
     * @return contentReplacements
     */
    public Map<String, String> getContentReplacements() { return this.contentReplacements; }

    /**
     * <p>set contentReplacement mapping</p>
     *
     * @param contentReplacements
     */
    public void setContentReplacements(Map<String, String> contentReplacements) { this.contentReplacements = contentReplacements; }

    /**
     * <p>get matchPatterns</p>
     *
     * @return matchPatterns
     */
    public List<String> getMatchPatterns() { return this.matchPatterns; }

    /**
     * <p>set matchPatterns</p>
     *
     * @param matchPatterns
     */
    public void setMatchPatterns(List<String> matchPatterns) { this.matchPatterns = matchPatterns; }

    /**
     * <p>get siteName</p>
     *
     * @return siteName
     */
    public String getSiteName() { return this.siteName; }

    /**
     * <p>set siteName</p>
     *
     * @param siteName site name
     */
    public void setSiteName(String siteName) {
        this.siteName = siteName;
    }

    /**
     * <p>get host</p>
     *
     * @return host
     */
    public String getHost() { return this.host; }

    /**
     * <p>set a mail host</p>
     *
     * @param host a mail host
     */
    public void setHost(String host) {
        this.host = host;
    }

    /**
     * <p>get port</p>
     *
     * @return port
     */
    public String getPort() { return this.port; }

    /**
     * <p>set a mail server port</p>
     *
     * @param port a mail server port
     */
    public void setPort(String port) {
        this.port = port;
    }

    /**
     * <p>get username</p>
     *
     * @return username
     */
    public String getUsername() { return this.username; }

    /**
     * <p>set the mail server username </p>
     *
     * @param username
     */
    public void setUsername(String username) {
        this.username = username;
    }

    /**
     * <p>get password</p>
     *
     * @return password
     */
    public String getPassword() { return this.password; }

    /**
     * <p>set the mail server password</p>
     *
     * @param password
     */
    public void setPassword(String password) {
        this.password = password;
    }

    /**
     * <p>get authenticated</p>
     *
     * @return authenticated
     */
    public boolean isAuthenticated() { return this.authenticated; }

    /**
     * <p>set if authentication is needed</p>
     *
     * @param authenticated
     */
    public void setAuthenticated(boolean authenticated) {
        this.authenticated = authenticated;
    }

    /**
     * <p>get protocol</p>
     *
     * @return protocol
     */
    public String getProtocol() { return this.protocol; }

    /**
     * <p>set a protocol</p>
     *
     * @param protocol
     */
    public void setProtocol(String protocol) { this.protocol = protocol; }

    /**
     * <p>get sendEmailFlagPath</p>
     *
     * @return sendEmailFlagPath
     */
    public String getSendEmailFlagPath() { return this.sendEmailFlagPath; }

    /**
     * <p>set sendEmailFlagPath</p>
     *
     * @param sendEmailFlagPath
     */
    public void setSendEmailFlagPath(String sendEmailFlagPath) { this.sendEmailFlagPath = sendEmailFlagPath; }


}