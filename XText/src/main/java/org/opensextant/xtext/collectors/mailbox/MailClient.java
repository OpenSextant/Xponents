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

import javax.mail.Authenticator;
import javax.mail.Folder;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.Store;

import org.opensextant.ConfigException;
import org.opensextant.xtext.XText;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author ubaldino at mitre
 * @author b. o'neill
 */
public class MailClient {

    Logger logger = LoggerFactory.getLogger(getClass());
    protected MailConfig config = null;
    protected String archiveRoot = null;

    public MailClient(MailConfig cfg, String archive) {
        config = cfg;
        archiveRoot = archive;
    }

    protected XText converter = null;

    public void setConverter(XText conversionManager) {
        converter = conversionManager;
    }

    /**
     * Close Mailbox cleanly.
     *
     * @throws MessagingException javamail error
     */
    public void disconnect() throws MessagingException {

        // Close connection
        if (folder != null) {
            folder.close(true);
        }
        if (store != null) {
            store.close();
        }
    }

    private Session session = null;
    private Store store = null;
    private Folder folder = null;

    /**
     * Connect to the mail server and establish the user + folder session.
     * @throws MessagingException javamail error
     */
    public void connect() throws MessagingException {
        logger.debug("host: {}, user: {}", config.getHost(), config.getUsername());
        logger.debug("setting session to debug: {}", config.isDebug());
        logger.debug("mailBoxFolder: {}", config.getMailFolder());

        // session = Session.getDefaultInstance(properties);
        Authenticator auth = config.getExchangeServerVersion() > 0 ? new NTLMAuth(config.getUsername(),
                config.getPassword()) : null;

        session = Session.getInstance(config, auth);

        session.setDebug(config.isDebug());
        store = session.getStore((config.isSSLEnabled() ? "imaps" : "imap"));

        store.connect(config.getHost(), config.getUsername(), config.getPassword());
        logger.debug("session connected?: " + store.isConnected());

        // Get folder

        folder = store.getFolder(config.getMailFolder());
        if (config.isReadOnly()) {
            folder.open(Folder.READ_ONLY);
            logger.debug("opened folder in READ_ONLY mode");

        } else {
            folder.open(Folder.READ_WRITE);
            logger.debug("opened folder in READ_WRITE mode");
        }

    }

    /**
     * Retrieve messages from the configured folder/inbox
     * @return array of messages
     * @throws MessagingException javamail error
     */
    public Message[] getMessages() throws MessagingException {
        if (folder != null) {
            return folder.getMessages();
        }
        return null;
    }

    /**
     * Tests the availability of the currently configured source.
     * @throws ConfigException err when testing indicates resource is not available
     */
    public void testAvailability() throws ConfigException {

        try {
            connect();
            disconnect();
        } catch (Exception err) {
            String msg = String.format("%s -- failed to collect mail account", getName());
            throw new ConfigException(msg, err);
        }
        return;
    }

    public void configure() throws ConfigException {
        // Test if the site exists and is reachable
        testAvailability();

        // Test is your destination archive exists
        if (archiveRoot != null) {
            File test = new File(archiveRoot);
            if (!(test.isDirectory() && test.exists())) {
                throw new ConfigException("Destination archive does not exist. Caller must create prior to creation.");
            }
        }
    }

    private String name = "Unnamed Mail Crawler";

    public void setName(String n) {
        name = n;
    }

    public String getName() {
        return name;
    }

}
