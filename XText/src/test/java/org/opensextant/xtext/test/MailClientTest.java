package org.opensextant.xtext.test;

import java.io.IOException;
import java.net.URL;

import javax.mail.MessagingException;

import org.opensextant.ConfigException;
import org.opensextant.xtext.collectors.mailbox.MailConfig;
import org.opensextant.xtext.collectors.mailbox.MailClient;

public class MailClientTest {

    /**
     * Simple connection demo.  No mail reading... just configure, connect, disconnect.
     * @param args
     */
    public static void main(String[] args) {
        try {
            System.out.println("Usage:  MailClientTest   cfg-url  /save/to/archive");
            MailConfig imapClientCfg;
            URL abc = MailClient.class.getResource(args[0]);
            imapClientCfg = new MailConfig(abc);
            
            MailClient imapClient = new MailClient(imapClientCfg, args[1]);
            imapClient.connect();
            imapClient.disconnect();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (ConfigException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (MessagingException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }
}
