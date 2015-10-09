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
package org.opensextant.xtext.collectors.sharepoint;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.http.auth.AuthScope;
import org.apache.http.auth.NTCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.CookieSpecs;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.HttpClientBuilder;
import org.opensextant.ConfigException;
import org.opensextant.xtext.collectors.web.WebClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Simple client that pulls down HTML from sharepoint, acquire files and crawl sub-folders.
 * Similar to WebClient, but not really...
 * This is not a general Sharepoint interface library nor a general Sharepoint crawling tool.
 * This *is* a barebones demonstration of how to interact with a Sharepoint site and capture meaningful content in
 * an SP site and its sub-folders.
 *
 */
public class SharepointClient extends WebClient {

    private final Logger log = LoggerFactory.getLogger(getClass());

    /**
     *  For testing:.
     *
     * @param siteUrl the site url
     * @param archive the archive
     * @param u user name
     * @param p password
     * @param dom domain
     * @throws MalformedURLException on err
     * @throws ConfigException on err
     */
    public SharepointClient(String siteUrl, String archive, String u, String p, String dom)
            throws MalformedURLException, ConfigException {
        super(siteUrl, archive);
        user = u;
        passwd = p;
        domain = dom;
    }

    private String user = null;
    private String passwd = null;
    private String domain = null;

    public void setUser(String u) {
        user = u;
    }

    public void setPasswd(String pw) {
        passwd = pw;
    }

    public void setDomain(String dom) {
        domain = dom;
    }

    private HttpClient currentConn = null;

    @Override
    public void reset(){
        currentConn = null;
    }

    /**
     * Sharepoint requires NTLM. This client requires a non-null user/passwd/domain.
     *
     */
    @Override
    public HttpClient getClient() {

        if (currentConn!=null){
            return currentConn;
        }

        HttpClientBuilder clientHelper = HttpClientBuilder.create();

        if (proxyHost != null) {
            clientHelper.setProxy(proxyHost);
        }

        RequestConfig globalConfig = RequestConfig.custom()
                .setCookieSpec(CookieSpecs.BROWSER_COMPATIBILITY).build();

        CredentialsProvider creds = new BasicCredentialsProvider();
        creds.setCredentials(AuthScope.ANY, new NTCredentials(user, passwd, server, domain));
        clientHelper.setDefaultCredentialsProvider(creds);
        HttpClient httpClient = clientHelper.setDefaultRequestConfig(globalConfig).build();

        return httpClient;

    }

    /**
     * Recursively parse a site page, limiting the crawl to local items
     * contained within the current folder/page.
     *
     * @param html HTML text buffer
     * @param pageUrl the page url
     * @return list of found sharepoint links
     */
    public Collection<SPLink> parseContentPage(String html, URL pageUrl) {
        Map<String, SPLink> contentLinks = new HashMap<String, SPLink>();
        Pattern href_matcher = Pattern.compile("href=\"([^\"]+)\"");
        Matcher matches = href_matcher.matcher(html);
        while (matches.find()) {
            String link = matches.group(1).trim();
            String link_lc = link.toLowerCase();
            if ("/".equals(link) || "#".equals(link) || link_lc.endsWith(".aspx")
                    || link_lc.startsWith("javascript") || link.startsWith("../")
                    || link.contains("/_layouts/")) {
                continue;
            }
            if (link.endsWith("/")) {
                link = link.substring(0, link.length() - 1);
            }
            try {
                SPLink l = new SPLink(prepURLPath(link).toString(), pageUrl);
                if (l.isResource()){
                    continue;
                }
                if (!contentLinks.containsKey(l.toString())) {
                    log.info("Found link {}", link);
                    contentLinks.put(l.toString(), l);
                }
            } catch (Exception err) {
                log.error("Failed to parse URL {}", link, err);
            }
        }

        return contentLinks.values();
    }
}
