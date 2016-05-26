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
import java.net.URLEncoder;
import java.security.NoSuchAlgorithmException;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.StringEscapeUtils;
import org.opensextant.util.TextUtils;
import org.opensextant.xtext.collectors.Collector;
import org.opensextant.xtext.collectors.web.HyperLink;

public class SPLink extends HyperLink {

    private URL simplifiedURL = null;

    /**
     * TODO: fix site vs. base link
     * 
     * @param link
     *            a URL
     * @param base
     *            where the URL was found
     * @throws MalformedURLException
     *             err that happens if URLs are poorly formatted.
     * @throws UnsupportedEncodingException
     *             err that happens occasionally
     * @throws NoSuchAlgorithmException
     *             err that never happens
     */
    public SPLink(String link, URL base)
            throws MalformedURLException, UnsupportedEncodingException, NoSuchAlgorithmException {
        super(link, base, base);

        if (isSharepointFolder()) {
            urlValue = StringEscapeUtils.unescapeHtml4(URLDecoder.decode(urlValue, "UTF-8"));
        }

        if (isAbsolute()) {
            absoluteURL = new URL(urlValue);
        } else {
            if (base == null) {
                throw new MalformedURLException("Unknown parent URL for arg baseUrl");
            }
            referrerURL = base; // aka Parent or containing page, folder or
            // other node
            absoluteURL = new URL(referrerURL, urlValue);
        }

        if (isSharepointFolder()) {
            parseURL();
            simplifiedURL = new URL(referrerURL, params.getProperty("RootFolder"));

            String[] pathParts = simplifiedURL.getPath().split("/");
            StringBuilder buf = new StringBuilder();
            for (String p : pathParts) {
                if (p.length() > 0) {
                    buf.append('/');
                    buf.append(encodePathSegment(p));
                }
            }
            simplifiedURL = new URL(simplifiedURL, buf.toString());
        }
    }

    public final String encodePathSegment(String s) throws UnsupportedEncodingException {
        StringBuilder buf = new StringBuilder();
        byte[] b = s.getBytes("UTF-8");
        for (byte bt : b) {
            if (isAlphanum(bt)) {
                buf.append((char)bt);
            } else {
                buf.append(escapeByte(bt));
            }
        }
        return buf.toString();

    }

    public static String escapeByte(byte b) {
        return String.format("%%%x", b);
    }

    public static boolean isAlphanum(byte b) {
        if (0x30 <= b && b <= 0x39) {
            return true;
        }
        if (0x40 < b && b < 0x5b) {
            return true;
        }
        if (0x60 < b && b < 0x7b) {
            return true;
        }

        return false;
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
            return String.format("%s%s%s", folder, Collector.PATH_SEP, getName());
        }
        return null;
    }

    public boolean isSharepointFolder() {
        return urlValue.contains("FolderCTID");
    }

    /**
     *
     * @return string for url if it could be simplified.
     * @throws MalformedURLException
     *             on err
     */
    public URL getSimplifiedFolderURL() throws MalformedURLException {
        if (simplifiedURL == null) {
            return null;
        }
        return simplifiedURL;
    }

    /**
     * parses out the RootFolder or returns the parent path part of the URL.
     *
     * @return cleaner version of a folder
     */
    public String getSharepointFolder() {
        if (isSharepointFolder()) {
            if (params.containsKey("RootFolder")) {
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
