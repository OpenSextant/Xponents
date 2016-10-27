/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.opensextant.extractors.flexpat;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

import org.opensextant.ConfigException;
import org.opensextant.extraction.Extractor;
import org.opensextant.extraction.TextMatch;
import org.opensextant.util.TextUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author ubaldino
 */
public abstract class AbstractFlexPat implements Extractor {

    /**
     * CHARS. SHP DBF limit is 255 bytes, so SHP file outputters should assess
     * at that time how/when to curtail match width. The max pre/post text seen
     * useful has typically been about 200-250 characters.
     */
    protected int match_width = 100;
    protected Logger log = LoggerFactory.getLogger(getClass());
    protected boolean debug = false;
    protected RegexPatternManager patterns = null;
    protected String patterns_file = null;

    public AbstractFlexPat() {
        debug = log.isDebugEnabled();
    }

    public AbstractFlexPat(boolean b) {
        this();
        debug = b;
    }

    /**
     * Create a pattern manager given the input stream and the file name.
     * @return the regex pattern manager
     * @throws java.net.MalformedURLException config error
     */
    protected abstract RegexPatternManager createPatternManager(InputStream s, String name)
            throws IOException;

    public RegexPatternManager getPatternManager() {
        return patterns;
    }

    /**
     * Configures whatever default patterns file is named.
     * @throws ConfigException config error, pattern file not found
     */
    @Override
    public void configure() throws ConfigException {
        if (patterns_file == null) {
            throw new ConfigException(
                    "Default configure() requires you set .patterns_file with a resource path to the item");
        }
        configure(getClass().getResourceAsStream(patterns_file), patterns_file);
    }

    /**
     * Configure using a particular pattern file.
     *
     * @param patfile a pattern file.
     * @throws ConfigException if pattern file not found
     */
    @Override
    public void configure(String patfile) throws ConfigException {
        if (patfile == null) {
            throw new ConfigException("Null path not allowed. no defaults.");
        }
        try {
            patterns = createPatternManager(new FileInputStream(patfile), patfile);
        } catch (Exception loaderr) {
            String msg = "Could not load patterns file FILE=" + patfile;
            throw new ConfigException(msg, loaderr);
        }
    }

    /**
     * Configure using a URL pointer to the pattern file.
     *
     * @param patfile patterns file URL
     * @throws ConfigException if pattern file not found
     */
    @Override
    public void configure(URL patfile) throws ConfigException {
        if (patfile == null) {
            throw new ConfigException("URL for pattern defs not found. ");
        }
        try {
            patterns = createPatternManager(patfile.openStream(), patfile.getFile());
        } catch (Exception loaderr) {
            throw new ConfigException("Could not load patterns file URL=" + patfile, loaderr);
        }
    }

    public void configure(InputStream strm, String name) throws ConfigException {
        try {
            patterns = createPatternManager(strm, name);
        } catch (Exception loaderr) {
            throw new ConfigException("Could not load patterns file =" + name, loaderr);
        }
    }

    /**
     * Match Width is the text buffer before and after a TextMatch. Match
     * buffers are used to create a match ID
     *
     * @param w width
     */
    public void setMatchWidth(int w) {
        match_width = w;
    }

    /**
     * Optional. Assign an identifier to each Text Match found. This is an MD5
     * of the match in-situ. If context is provided, it is used to generate the
     * identity.  If a count is provided it is used.
     *
     * otherwise make use of just pattern ID + text value.
     *
     * @param m a TextMatch
     * @param count incrementor used for uniqueness
     */
    protected void set_match_id(TextMatch m, int count) {
        try {
            if (m.getContextBefore() == null) {
                m.match_id = TextUtils.text_id(String.format("%s,%s", m.pattern_id, m.getText()));
            } else if (count >= 0) {
                m.match_id = TextUtils.text_id(String.format("%s,%s,%d", m.pattern_id, m.getText(),
                        count));
            } else {
                StringBuilder abc = new StringBuilder();
                abc.append(m.getContextBefore());
                abc.append(m.getText());
                abc.append(m.getContextAfter());
                m.match_id = TextUtils.text_id(abc.toString());
            }
        } catch (Exception hashErr) {
            log.error("Rare Java cryptologic err", hashErr);
        }
    }

    public void enableAll() {
        patterns.enableAll();
    }

    public void disableAll() {
        patterns.disableAll();
    }

    public void updateProgress(double progress) {
    }

    public void markComplete() {
    }
}
