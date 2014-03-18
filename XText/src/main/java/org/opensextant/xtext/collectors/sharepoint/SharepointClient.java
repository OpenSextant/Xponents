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
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.http.auth.AuthScope;
import org.apache.http.auth.NTCredentials;
import org.apache.http.client.HttpClient;
import org.apache.http.client.params.ClientPNames;
import org.apache.http.client.params.CookiePolicy;
import org.apache.http.conn.params.ConnRoutePNames;
import org.apache.http.impl.client.DefaultHttpClient;
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

    private Logger log = LoggerFactory.getLogger(getClass());

    /** For testing: 
     * @throws MalformedURLException 
     * @throws ConfigException 
     */
    public SharepointClient(String siteUrl, String archive, String u, String p, String dom) throws MalformedURLException, ConfigException {
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

    /**
     * Sharepoint requires NTLM
     */
    @Override
    public HttpClient getClient() {
        HttpClient httpClient = new DefaultHttpClient();

        ((DefaultHttpClient) httpClient).getCredentialsProvider().setCredentials(AuthScope.ANY,
                new NTCredentials(user, passwd, server, domain));

        /*
         * 
         */
        if (proxyHost != null) {
            httpClient.getParams().setParameter(ConnRoutePNames.DEFAULT_PROXY, proxyHost);
        }

        httpClient.getParams().setParameter(ClientPNames.COOKIE_POLICY, CookiePolicy.BROWSER_COMPATIBILITY);
        return httpClient;

    }

    /**
     * Recursively parse a site page, limiting the crawl to local items
     * contained within the current folder/page
     * 
     * @param html
     *            HTML text buffer
     * @return
     */
    public Collection<SPLink> parseContentPage(String html, String pageUrl) {
        Map<String, SPLink> contentLinks = new HashMap<String, SPLink>();
        Pattern href_matcher = Pattern.compile("href=\"([^\"]+)\"");
        Matcher matches = href_matcher.matcher(html);
        while (matches.find()) {
            String link = matches.group(1).trim();
            String link_lc = link.toLowerCase();
            if ("/".equals(link) || "#".equals(link) || link_lc.endsWith(".aspx") || link_lc.startsWith("javascript")
                    || link.startsWith("../") || link.contains("/_layouts/")) {
                continue;
            }
            if (link.endsWith("/")) {
                link = link.substring(0, link.length() - 1);
            }
            try {
                SPLink l = new SPLink(link, pageUrl);
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
