package org.opensextant.extractors.geo;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import org.opensextant.ConfigException;
import org.opensextant.data.MatchSchema;
import org.opensextant.data.TextInput;
import org.opensextant.extraction.ExtractionException;
import org.opensextant.extraction.Extractor;
import org.opensextant.extraction.TextMatch;
import org.opensextant.extractors.geo.rules.GeocodeRule;
import org.opensextant.extractors.geo.rules.PostalCodeFilter;

/**
 * Postal Tagger tags and returns any alphanumeric token or phrase that resembles postal codes
 * and abbreviations.  This includes simple filter rules, and nothing attempting geocoding.
 *
 * @author ubaldino
 */
public class PostalTagger extends GazetteerMatcher implements MatchSchema, Extractor {

    public static final String VERSION = "1.0";
    public static final String METHOD_DEFAULT = String.format("PostalTagger v%s", VERSION);
    private static final String NO_TEXT_ID = "no-id";

    /**
     * DEFAULT - postal codes 4 chars or longer are considered, e.g.,
     * <p>
     * G81-4
     * G814
     * 0174
     * 011 -- too short and ambiguous.
     */
    private static final int MIN_LEN = 4;
    private int minLen = MIN_LEN;
    GeocodeRule triage = new PostalCodeFilter(minLen);

    public PostalTagger() throws ConfigException {
        super();

        /* No lower case; Allow code/digits;  Do not filter on stopwords */
        this.setAllowLowerCase(false);
        this.setAllowLowerCaseAbbreviations(false);
        this.setEnableCaseFilter(false);
        this.setEnableCodeHunter(true);
    }

    @Override
    public String getName() {
        return "PostalTagger";
    }

    @Override
    public String getCoreName() {
        return "postal";
    }

    @Override
    public void configure() {
    }

    @Override
    public void configure(String patfile) throws ConfigException {
        throw new ConfigException("Not an option for this extractor");
    }

    @Override
    public void configure(URL patfile) throws ConfigException {
        throw new ConfigException("Not an option for this extractor");
    }

    /**
     * Tag, choose location if possible and emit an array of text matches.
     * <p>
     * INPUT:  Free text that may have postal addresses.
     * <p>
     * OUTPUT:
     * TextMatch array of all possible postal codes that pass trivial noise filters.
     *
     * @param input TextInput
     * @return array of TextMatch
     * @throws ExtractionException if extraction fails (Solr or Lucene errors) or rules mechanics.
     */
    @Override
    public List<TextMatch> extract(TextInput input) throws ExtractionException {
        List<PlaceCandidate> candidates = tagText(input, false);
        triage.evaluate(candidates);
        return new ArrayList<>(candidates);
    }

    @Override
    public List<TextMatch> extract(String input) throws ExtractionException {
        return this.extract(new TextInput(NO_TEXT_ID, input));
    }

    /**
     * Very simple resource reporting and cleanup.
     */
    @Override
    public void cleanup() {
        this.reportMemory();
        close();
    }

    /**
     * Override the default MIN_LEN=4 length for a postal code.  Any textmatch with length &lt; this length will
     * be filtered out.
     * Postal codes in CA, FO, GB, GG, IE, IM, IS, JE, MT all have postal codes that are 2 or 3 alphanum.
     */
    public void setMinLen(int l) {
        minLen = l;
    }
}
