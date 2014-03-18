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

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLDecoder;

import org.apache.commons.lang.StringEscapeUtils;
import org.opensextant.xtext.collectors.web.HyperLink;

public class SPLink extends HyperLink {

    private URL simplifiedURL = null;

    // private String serverURL = null;

    /**
     * TODO: fix site vs. base link
     * @param link
     * @param base
     * @throws MalformedURLException
     * @throws UnsupportedEncodingException
     */
    public SPLink(String link, String base) throws MalformedURLException, UnsupportedEncodingException {
        super(link, base, base);

        if (isSharepointFolder()) {
            urlValue = StringEscapeUtils.unescapeHtml(URLDecoder.decode(urlValue, "UTF-8"));
        }

        if (isAbsolute()) {
            absoluteURL = new URL(urlValue);
        } else {
            if (base == null) {
                throw new MalformedURLException("Unknown parent URL for arg baseUrl");
            }
            baseURL = new URL(base); // aka Parent or containing page, folder or
                                     // other node
            absoluteURL = new URL(baseURL, urlValue);
        }

        if (isSharepointFolder()) {
            parseURL();
            simplifiedURL = new URL(baseURL, params.getProperty("RootFolder"));
        }
    }


    /**
     * Converts an obfuscated Sharepoint folder URL or view into a normal path
     * name.
     * 
     * @return a normal looking Unix/URL path; or null if not possible.
     */
    @Override
    public String getNormalPath() {
        String folder = getSharepointFolder();
        if (folder != null) {
            return String.format("%s%s%s", folder, File.separator, getName());
        }
        return null;
    }


    public boolean isSharepointFolder() {
        return urlValue.contains("FolderCTID");
    }

    /**
     * 
     * @return string for url if it could be simplified.
     */
    public String getSimplifiedFolderURL() {
        if (simplifiedURL == null) {
            return null;
        }
        return simplifiedURL.toString();
    }

    /**
     * parses out the RootFolder or returns the parent path part of the URL.
     * 
     * @return
     */
    public String getSharepointFolder() {
        if (isSharepointFolder()) {
            if (params.contains("RootFolder")) {
                return params.getProperty("RootFolder");
            }

        } else {
            if (hasPath()) {
                String p = absoluteURL.getPath();
                return new File(p).getParent();
            }
        }
        return null;
    }


}
