/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.opensextant.extractors.flexpat;

import java.net.URL;
import org.opensextant.extraction.Extractor;
import org.opensextant.extraction.ConfigException;
import org.opensextant.extraction.TextMatch;
import org.opensextant.util.TextUtils;
import org.slf4j.Logger;

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
    protected Logger log = null;
    protected boolean debug = false;
    protected String patterns_file = null;
    protected URL patterns_url = null;
    protected RegexPatternManager patterns = null;
    private final TextUtils utility = new TextUtils();

    public AbstractFlexPat() {
    }

    public AbstractFlexPat(boolean b) {
        debug = b;
    }

    /**
     * Create Patterns Manager given the result of configure(?) which is a URL
     * (preferred) or a path; If a URL is set it is used.
     */
    protected abstract RegexPatternManager createPatternManager() throws java.net.MalformedURLException;

    public RegexPatternManager getPatternManager(){
        return patterns;
    }

    /**
     * Configure with the default coordinate patterns file, geocoord_regex.cfg
     * in CLASSPATH
     *
     * @throws ConfigException
     */
    @Override
    public void configure() throws ConfigException {
        configure(getClass().getResource(patterns_file)); // default
    }

    /**
     * Configure using a particular pattern file.
     *
     * @param patfile
     * @throws ConfigException
     */
    @Override
    public void configure(String patfile) throws ConfigException {
        if (patfile != null) {
            patterns_file = patfile;
        }
        try {
            patterns = createPatternManager();// new PatternManager(patterns_file.trim());
            patterns.testing = debug;
            patterns.initialize();
        } catch (Exception loaderr) {
            String msg = "Could not load patterns file FILE=" + patterns_file;
            log.error(msg, loaderr);
            throw new ConfigException(msg, loaderr);
        }
    }

    /**
     * Configure using a URL pointer to the pattern file.
     *
     * @param patfile
     * @throws ConfigException
     */
    @Override
    public void configure(URL patfile) throws ConfigException {
        if (patfile == null) {
            configure();
        } else {
            try {
                patterns_url = patfile;
                patterns_file = patfile.getFile();
                patterns = createPatternManager();
                patterns.testing = debug;
                patterns.initialize();
            } catch (Exception loaderr) {
                String msg = "Could not load patterns file URL=" + patfile;
                log.error(msg, loaderr);
                throw new ConfigException(msg, loaderr);
            }
        }
    }

    /**
     * Match Width is the text buffer before and after a TextMatch. Match
     * buffers are used to create a match ID
     *
     * @see TextMatch.createID
     * @param w
     */
    public void setMatchWidth(int w) {
        match_width = w;
    }

    /**
     * (Optional) Assign an identifier to each Text Match found. This is an MD5
     * of the match in-situ. If context is provided, it is used to generate the
     * identity
     *
     * otherwise make use of just pattern ID + text value.
     *
     * @param m a TextMatch
     */
    protected void set_match_id(TextMatch m) {
        if (m.getContextBefore() == null) {
            m.match_id = utility.genTextID(m.pattern_id + m.getText());
        } else {
            m.match_id = utility.genTextID(m.getContextBefore() + m.getText() + m.getContextAfter());
        }
    }

    public void enableAll() {
        patterns.enableAll();
    }

    public void disableAll() {
        patterns.disableAll();
    }

/*
    public void enablePatterns(String prefix);

    public void disablePatterns(String prefix);
    * */
}
