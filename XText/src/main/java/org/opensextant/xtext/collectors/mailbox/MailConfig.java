/**
 *
 * Copyright 2013-2014 OpenSextant.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package org.opensextant.xtext.collectors.mailbox;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.security.GeneralSecurityException;
import java.util.Properties;

import org.apache.commons.lang.StringUtils;

import com.sun.mail.util.MailSSLSocketFactory;
import org.opensextant.ConfigException;

/**
 * See reference property file at src/test/resources/collectors/imap-templ.cfg
 * This IMAP template documents the various parameters for configuring a Java mail client to use IMAP or IMAP/SSL
 * 
 * @author ubaldino
 * @author b. o'neill
 *
 */
public class MailConfig extends Properties {

    /**
     * 
     */
    private static final long serialVersionUID = 1L;

    public static int READ_ALL = -1;
    public static String DEFAULT_IMAP_PORT = "143";
    public static String DEFAULT_IMAPS_PORT = "993";

    private boolean debug = false;

    // These must be provides
    private String host = null;
    private String keyStore = null;
    private String storePass = null;
    private String trustStore = null;
    private String username = null;
    private String password = null;

    private String mailFolder = "Inbox";

    private int maxMessagesToRead = READ_ALL;
    private boolean readOnly = false;
    private boolean readNewMessagesOnly = false;
    private boolean deleteOld = true;
    private boolean deleteOnRead = true;
    private int exchangeServer = -1;

    private boolean is_SSL = false;

    /**
     * Default mail client configuration that inherits System/OS properties
     */
    public MailConfig() {
        super(System.getProperties());
    }

    /**
     * Configure from a file or classpath. Mail client configuration that
     * inherits System/OS properties, then overrides defaults from parameters in
     * given confg file.
     * 
     * @param propsFilePath
     * @throws IOException
     * @throws CrawlerException
     */
    public MailConfig(String propsFilePath) throws IOException, ConfigException {
        super(System.getProperties());

        InputStream fileInputStream = null;
        fileInputStream = new FileInputStream(propsFilePath);

        this.load(fileInputStream);
        fileInputStream.close();
        setProperties();
    }

    public MailConfig(URL cfgUrl) throws IOException, ConfigException {
        super(System.getProperties());

        InputStream io = cfgUrl.openStream();
        this.load(io);
        io.close();

        setProperties();
    }

    /**
     * Given this configuration, determine if the messages read so far (curr) is
     * at the total available
     * 
     * or if config specified a max read count, then if curr > max read, return
     * true.
     * 
     * @param total
     * @param curr
     * @return
     */
    public boolean doneReading(int total, int curr) {
        if (this.getMaxMessagesToRead() < 0) {
            return curr >= total;
        }

        return curr >= getMaxMessagesToRead();
    }

    /**
     * Set default properties, interpreting System props and any props loaded
     * from config file. Various flags are set as a result of interpreting the
     * key/value pairs.
     * 
     * @throws ConfigException
     */
    public void setProperties() throws ConfigException {
        setProperties(null);
    }

    /**
     * Set properties from existing Property sheet.
     * 
     * @param props
     * @throws ConfigException
     */
    public void setProperties(Properties props) throws ConfigException {

        if (props != null) {
            for (Object o : props.keySet()) {
                setProperty(o.toString(), props.getProperty((String) o));
            }
        }
        validateBasicSettings();
        setConfiguration();

        is_SSL = getFlagProperty(getProperty("mail.imap.ssl.enable"));

        if (is_SSL) {
            validateCertificates();
        }
        try {
            setAdvancedSettings();
        } catch (Exception securityErr) {
            throw new ConfigException("Advanced settings failed", securityErr);

        }
    }

    /**
     * 
     * @throws ConfigException
     */
    private void validateCertificates() throws ConfigException {
        if (StringUtils.isBlank(getKeyStore())) {
            return;
        }
        if (!new File(getKeyStore()).exists()) {
            throw new ConfigException("Keystore set, but is invalid file");
        }
        System.setProperty("javax.net.ssl.keyStore", getKeyStore());

        if (StringUtils.isNotBlank(getStorepass())) {
            System.setProperty("javax.net.ssl.keyStorePassword", getStorepass());
        } else {
            throw new ConfigException("Keystore set, but no store pass provided");
        }

        // Trust Store is relatively unused. No references to it in code.
        // But may be used under the hood in java-mail API
        //
        if (StringUtils.isNotBlank(getTrustStore())) {
            System.setProperty("javax.net.ssl.trustStore", getTrustStore());
        }
    }

    private void validateBasicSettings() throws ConfigException {
        String proto = getProperty("mail.protocol");
        if (!("imap".equalsIgnoreCase(proto) || "imaps".equalsIgnoreCase(proto))) {
            throw new ConfigException("IMAP is only mailbox protocol supported currently");
        }
    }

    public boolean isSSLEnabled() {
        return is_SSL;
    }

    /**
     * Parameters supported:
     * 
     * <pre>
     * ## Mail client behavior
     * mail.read.count=10
     * mail.read.readonly=true
     * mail.read.newonly=true
     * mail.read.delete_after=false
     * mail.delete.purge_old=false
     * mail.debug=true
     * 
     * ## Connection
     * #####################
     * mail.host=imccas03.mitre.org
     * mail.user=ubaldino
     * mail.password=XXXXXXXX
     * mail.folder=Inbox
     * mail.protocol=imap
     * 
     * # SSL, Certificates
     * #####################
     * keystore
     * storepass
     * truststore
     * 
     * # Protocol: IMAP
     * #####################
     * mail.imap.starttls.enable=true
     * mail.imap.socketFactory.fallback=false
     * mail.imap.socketFactory.port
     * mail.imap.ssl.enable=true
     * mail.imap.socketFactory.class=javax.net.SocketFactory
     * mail.imaps.class=com.sun.mail.IMAPSSLStore
     * 
     * mail.microsoft.exchange.version=2007
     * mail.imap.auth.ntlm.domain=MITRE
     * 
     * 
     * Java Mail params:
     *     mail.imap.auth.ntlm.domain
     *     mail.imap.starttls.enable
     *     mail.imap.socketFactory.fallback
     *     mail.imap.socketFactory.port
     *     mail.imap.ssl.enable
     *     mail.imap.socketFactory.class
     * 
     * </pre>
     * 
     */
    protected boolean setConfiguration() {
        boolean foundParameters = false;

        for (Object key : keySet()) {

            String keyName = key.toString();
            String value = getProperty(keyName);

            if ("mail.host".equals(keyName)) {
                foundParameters = true; // Required.
                this.host = value;
            } else if ("keystore".equals(keyName)) {
                this.keyStore = value;
            } else if ("storepass".equals(keyName)) {
                this.storePass = value;
            } else if ("truststore".equals(keyName)) {
                this.trustStore = value;
            } else if ("mail.user".equals(keyName)) {
                this.username = value;
            } else if ("mail.password".equals(keyName)) {
                this.password = value;
            } else if ("mail.folder".equals(keyName)) {
                this.mailFolder = value;
            } else if ("mail.read.count".equals(keyName)) {
                this.maxMessagesToRead = getIntegerProperty(value);
            } else if ("mail.read.readonly".equals(keyName)) {
                this.readOnly = getFlagProperty(value);
            } else if ("mail.read.newonly".equals(keyName)) {
                this.readNewMessagesOnly = getFlagProperty(value);
            } else if ("mail.delete.purge_old".equals(keyName)) {
                this.deleteOld = getFlagProperty(value);
            } else if ("mail.debug".equals(keyName)) {
                this.debug = getFlagProperty(value);
            } else if ("mail.read.delete_after".equals(keyName)) {
                this.deleteOld = getFlagProperty(value);
            } else if ("mail.microsoft.exchange.version".equals(keyName)) {
                exchangeServer = getIntegerProperty(value);
            }
        }

        return foundParameters;
    }

    /**
     * A conveneince wrapper around setting the SSL socket factory and other parameters.
     * @throws GeneralSecurityException
     */
    public void setAdvancedSettings() throws GeneralSecurityException {

        if (this.isSSLEnabled()) {
            MailSSLSocketFactory socketFactory = new MailSSLSocketFactory();
            socketFactory.setTrustAllHosts(true);
            this.put("mail.imaps.ssl.socketFactory", socketFactory);
            // Default?
            // setProperty("mail.imap.socketFactory.class",
            // "javax.net.ssl.SSLSocketFactory");
        }
    }

    public String getDefaultPort(boolean useSSL) {
        return (useSSL ? DEFAULT_IMAPS_PORT : DEFAULT_IMAP_PORT);
    }

    /**
     * Return -1 if unknown or if it does not matter. 2003, 2007, 2010
     * 
     * @return
     */
    public int getExchangeServerVersion() {
        return exchangeServer;
    }

    /**
     * Defaults to -1 if no value found
     * 
     * @param val
     * @return
     */
    protected static int getIntegerProperty(Object val) {
        if (val == null) {
            return -1;
        }
        return Integer.parseInt(val.toString());
    }

    /**
     * get a flag. DEFAULT: false
     * 
     * @param val
     * @return
     */
    protected static boolean getFlagProperty(Object val) {
        if (val == null) {
            return false;
        }
        return Boolean.parseBoolean(val.toString());
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public String getKeyStore() {
        return keyStore;
    }

    public void setKeyStore(String keyStore) {
        this.keyStore = keyStore;
    }

    public String getStorepass() {
        return storePass;
    }

    public void setStorepass(String keyStoreKey) {
        this.storePass = keyStoreKey;
    }

    public String getTrustStore() {
        return trustStore;
    }

    public void setTrustStore(String trustStore) {
        this.trustStore = trustStore;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String userName) {
        this.username = userName;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String userKey) {
        this.password = userKey;
    }

    public String getMailFolder() {
        return mailFolder;
    }

    public void setMailFolder(String mailFolder) {
        this.mailFolder = mailFolder;
    }

    public int getMaxMessagesToRead() {
        return maxMessagesToRead;
    }

    public void setMaxMessagesToRead(int maxMessagesToRead) {
        if (maxMessagesToRead <= READ_ALL) {
            this.maxMessagesToRead = READ_ALL;
        } else {
            this.maxMessagesToRead = maxMessagesToRead;
        }
    }

    public boolean isReadOnly() {
        return readOnly;
    }

    public void setReadOnly(boolean readOnly) {
        this.readOnly = readOnly;
    }

    public boolean isReadNewMessagesOnly() {
        return readNewMessagesOnly;
    }

    public void setReadNewMessagesOnly(boolean readNewMessagesOnly) {
        this.readNewMessagesOnly = readNewMessagesOnly;
    }

    public boolean isDeleteOld() {
        return deleteOld;
    }

    public void setDeleteOld(boolean deleteOld) {
        this.deleteOld = deleteOld;
    }

    public boolean isDebug() {
        return debug;
    }

    public void setDebug(boolean debug) {
        this.debug = debug;
    }

    public boolean isDeleteOnRead() {
        return deleteOnRead;
    }

    public void setDeleteOnRead(boolean deleteOnRead) {
        this.deleteOnRead = deleteOnRead;
    }

}
