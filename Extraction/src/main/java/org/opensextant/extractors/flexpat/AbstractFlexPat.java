/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.opensextant.extractors.flexpat;

import java.net.URL;

import org.opensextant.ConfigException;
import org.opensextant.extraction.Extractor;
import org.opensextant.extraction.TextMatch;
import org.opensextant.processing.progress.ProgressMonitor;
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
    private ProgressMonitor progressMonitor;

    public AbstractFlexPat() {
    }

    public AbstractFlexPat(boolean b) {
        debug = b;
    }

    /**
     * Create Patterns Manager given the result of configure(?) which is a URL
     * (preferred) or a path; If a URL is set it is used.
     *
     * @return the regex pattern manager
     * @throws java.net.MalformedURLException config error
     */
    protected abstract RegexPatternManager createPatternManager()
            throws java.net.MalformedURLException;

    public RegexPatternManager getPatternManager() {
        return patterns;
    }

    /**
     * Configures whatever default patterns file is named.
     * @throws ConfigException config error, pattern file not found
     */
    @Override
    public void configure() throws ConfigException {
        // Default behavior - get the default patterns file.
        patterns_url = getClass().getResource(patterns_file);
        if (patterns_url == null) {
            throw new ConfigException("Did not find your configuration file=" + patterns_file);
        }
        configure(patterns_url);
    }

    /**
     * Configure using a particular pattern file.
     *
     * @param patfile a pattern file.
     * @throws ConfigException if pattern file not found
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
     * @param patfile patterns file URL
     * @throws ConfigException if pattern file not found
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

    @Override
    public void setProgressMonitor(ProgressMonitor progressMonitor) {
        this.progressMonitor = progressMonitor;
    }

    @Override
    public void updateProgress(double progress) {
        if (this.progressMonitor != null)
            progressMonitor.updateStepProgress(progress);
    }

    @Override
    public void markComplete() {
        if (this.progressMonitor != null)
            progressMonitor.completeStep();
    }
}
