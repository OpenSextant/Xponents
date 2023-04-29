package org.opensextant.extractors.geo;

import java.io.IOException;
import java.net.URL;
import java.util.*;

import org.opensextant.ConfigException;
import org.opensextant.data.Country;
import org.opensextant.data.MatchSchema;
import org.opensextant.data.Place;
import org.opensextant.data.TextInput;
import org.opensextant.extraction.ExtractionException;
import org.opensextant.extraction.Extractor;
import org.opensextant.extraction.TextMatch;
import org.opensextant.extractors.geo.rules.*;
import org.opensextant.util.GeonamesUtility;
import org.opensextant.util.TextUtils;

/**
 * PostalGeocoder -- a GazetteerMatcher that uses the "postal" solr index to
 * quickly tag any known postal codes worldwide.
 * Postal codes are typically 4 to 7 alphanumeric characters
 * with space or punctuation.
 * Through Geonames.org we have identified 4 million unique patterns for
 * COUNTRY + CODE tuples.
 * <p>
 * For example the Postal code "11111" in different countries is two distinct
 * codes, since we assume a postal code is unique within a country, but may
 * occur in more than one country.
 * <p>
 * Xponents Methodology:
 * <p>
 * - "Rules" are added to PlaceCandidates to inform caller of basic lexical rules fired
 * - "PlaceEvidence" is NOT used to score Places, because there is very little geographic
 * association across tags
 * - Confidence is assigned to a PlaceCandidate only based on complexity of the match
 * <p>
 * Returned "TextMatch" tags are marked as filtered_out for SHORT or YEAR codes.
 * Returned "TextMatch" tags may or may not have a location selected.
 *
 * @author ubaldino
 */
public class PostalGeocoder extends GazetteerMatcher implements MatchSchema, Extractor, BoundaryObserver, CountryObserver {

    public static final String VERSION = "1.0";
    public static final String METHOD_DEFAULT = String.format("PostalGeocoder v%s", VERSION);
    private static final String NO_TEXT_ID = "no-id";

    /**
     * DEFAULT - postal codes 4 chars or longer are considered, e.g.,
     * <p>
     * G81-4
     * G814
     * 0174
     * 011 -- too short and ambiguous.
     */
    private static final int MIN_LEN = 3;
    private int minLen = MIN_LEN;
    private final List<GeocodeRule> rules = new ArrayList<>();
    private Map<String, Country> countryCatalog = null;

    private final HashMap<String, PlaceCount> pairedPostalMentions = new HashMap<>();
    private final HashMap<String, CountryCount> inferredCountries = new HashMap<>();

    private final PostalCodeAssociationRule assocFilter = new PostalCodeAssociationRule();

    public PostalGeocoder() throws ConfigException {
        super();

        /* No lower case; Allow code/digits;  Do not filter on stopwords */
        this.setAllowLowerCase(false);
        this.setAllowLowerCaseAbbreviations(false);
        this.setEnableCaseFilter(false);
        this.setEnableCodeHunter(true);

        // LEXICAL FILTERS
        rules.add(new PostalCodeFilter(minLen));

        // HIERARCHICAL ASSOCIATION
        assocFilter.setBoundaryObserver(this);
        assocFilter.setCountryObserver(this);
        rules.add(assocFilter);

        //  OMIT UNASSOCIATED CODES that look like YEAR numbers
        rules.add(new PostalCodeYearFilter());

        // DISAMBIGUATION
        GeocodeRule chooser = new PostalLocationChooser();
        chooser.setDefaultMethod(METHOD_DEFAULT);
        rules.add(chooser);
    }

    @Override
    public String getName() {
        return "PostalGeocoder";
    }

    @Override
    public String getCoreName() {
        return "postal";
    }

    @Override
    public void configure() throws ConfigException {
        /*
         * TODO: Figure out any GeocodeRules that help manage precision.
         */
        try {
            GeonamesUtility geodataUtil = new GeonamesUtility();
            countryCatalog = geodataUtil.getISOCountries();

        } catch (IOException err) {
            throw new ConfigException("Failed to load country metadata", err);
        }
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
     * TextMatch arrary where each match may be:
     * <ul>
     *     <li>high confidence: Admin code + Postal code that makes sense
     *     </li>
     *     <li>
     *         low confidence: Postal code alone
     *     </li>
     * </ul>
     * <p>
     *   There is nothing in between really, for example:
     *
     *   <pre>
     *       ..... CA  94537 ...     # a valid zip code in California next to "CA" postal abbreviation. HIGH confidence
     *       ..... 94537 ....        # a bare 5-digit number. LOW confidence.
     *       ..... SA6 19DN ...      # bare alpha-numeric postal code.  MED confidence
     *   </pre>
     *
     * @param input TextInput
     * @return array of TextMatch
     * @throws ExtractionException if extraction fails (Solr or Luecene errors) or rules mechanics.
     */
    @Override
    public List<TextMatch> extract(TextInput input) throws ExtractionException {
        List<PlaceCandidate> candidates = tagText(input, false);
        reset();
        /* assess each tag.  Rules filter, improve, and then choose and rate confidence on each match */
        assocFilter.setBuffer(input.buffer);
        for (GeocodeRule r : rules) {
            r.evaluate(candidates);
        }
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


    public void reset() {
        pairedPostalMentions.clear();
        inferredCountries.clear();
        assocFilter.setBuffer(null);
    }

    @Override
    public void boundaryLevel1InScope(String nameNorm, Place p) {
        String key = p.getHierarchicalPath();
        PlaceCount cnt = pairedPostalMentions.computeIfAbsent(key, newCount -> new PlaceCount(key));
        ++cnt.count;
    }

    @Override
    public void boundaryLevel2InScope(String nameNorm, Place p) { /* no-op */}

    @Override
    public Map<String, PlaceCount> placeMentionCount() {
        return pairedPostalMentions;
    }

    @Override
    public void countryInScope(String cc) {
        Country C = countryCatalog.get(cc);
        if (C == null) {
            log.debug("Unknown country code {}", cc);
            return;
        }
        CountryCount counter = inferredCountries.computeIfAbsent(C.getCountryCode(), newCount -> new CountryCount(C));
        ++counter.count;
    }

    @Override
    public void countryInScope(Country C) {
        countryInScope(C.getCountryCode());
    }

    @Override
    public boolean countryObserved(String cc) {
        return inferredCountries.containsKey(cc);
    }

    @Override
    public boolean countryObserved(Country C) {
        return inferredCountries.containsKey(C.getCountryCode());
    }

    @Override
    public int countryCount() {
        return inferredCountries.size();
    }

    @Override
    public Map<String, CountryCount> countryMentionCount() {
        return inferredCountries;
    }

    private static void copyMatchId(TextMatch postal, List<TextMatch> matches) {
        for (TextMatch m : matches) {
            if (m instanceof PlaceCandidate && m.isSameMatch(postal)) {
                postal.match_id = m.match_id;
                postal.setType(m.getType());
                return;
            }
        }
    }

    /**
     * Given geotagging from a prior pass of PlaceGeocoder or other stuff, compare and align
     * those tags with POSTAL tags.
     */
    public static void associateMatches(List<TextMatch> matches, List<TextMatch> postalMatches) {

        /* Loop through matches;  Not guaranteeing that an array of matches is sorted
         * by document appearance.  We loop through both arrays fully -- using the postal Matches as the main loop
         *
         * If Not yet geolocated:
         *    -- try to attach ANY city,country or landmark to this location where ADM1 levels match, at least.
         * If geolocated by postal coder already
         *    -- try to attach OTHER "" ""
         *
         */
        for (TextMatch p1 : postalMatches) {
            if (!(p1 instanceof PlaceCandidate)) {
                // This shant' happen.
                continue;
            }
            if (p1.isFilteredOut()) {
                continue;
            }

            if (TextUtils.isNumeric(p1.getText()) && p1.getLength() < MIN_LEN) {
                // Ignore numeric codes that are 3 digits or shorter, for now.
                // Ho hum... a limitation, but trivial matches may slow performance down.
                continue;
            }
            copyMatchId(p1, matches);
            PlaceCandidate postal = (PlaceCandidate) p1;
            if (!postal.hasPostal()) {
                continue;
            }

            int proximity = 20; // chars
            // Postal is NOW a Postal code.
            for (TextMatch m1 : matches) {
                /*
                Potential scenario: "postal match" is the anchor.

                    <City>, <Province>, <Postal Code>  <Country>
                                         ANCHOR
                             RELATED ------+
                    OTHER------------------|------------OTHER

                    Loop through surrounding matches and associate highest resolution in order:
                        P/PPL  -- preffered
                        A/ADM* -- second
                        A/PCL* -- third

                   RESULT:
                         match#1   <Postal Code>
                         match#2  inferred, complete span from |<City> to <Country>|
                 */
                if (!(postal.isWithinChars(m1, proximity) && m1 instanceof PlaceCandidate)) {
                    continue;
                }
                // Associate postal with m1?
                PlaceCandidate other = (PlaceCandidate) m1;

                // Link specific slots by feeature type --- The geography on the other match needs to line up with the postal geo.
                // If postal geo is chosen, use it.
                //
                // Result is "postal" PlaceCandidate now has a fully linked set of related slots.
                // Prior taggers may have already made linkages.
                linkGeography(postal, other, "city", "P/PPL");
                linkGeography(postal, other, "admin", "A/ADM");
                linkGeography(postal, other, VAL_COUNTRY, "A/PCL");
            }
            // Linkages above will re-score geographic connections between postal ~ city, admin, country.
            //   In the end you will have 4 possible geolocations to choose from, the postal coding be the most specific.
            postal.setChosen(null);
            postal.choose();
        }
    }

    public static boolean linkGeography(PlaceCandidate postal, PlaceCandidate otherMention, String slot, String featPrefix) {

        if (postal.hasLinkedGeography(slot)) {
            return true;
        }

        // Dare we cache the sorted scoredPlaces for each mention/otherMention?
        //
        // "postal" geo  ~ "02144"   ( KR.11 or US.MA )
        for (ScoredPlace geoScore : postal.getPlaces()) {
            //  slot = "admin",  if otherMention is "Massachussetts", then selected postal geo is "02144"(US.MA)
            Place geo = geoScore.getPlace();
            for (ScoredPlace otherGeoScore : otherMention.getPlaces()) {
                Place otherGeo = otherGeoScore.getPlace();
                if (otherGeo.getFeatureDesignation().startsWith(featPrefix)) {
                    if (geo.sameBoundary(otherGeo)) {
                        postal.linkGeography(otherMention, slot, otherGeo);
                        postal.incrementPlaceScore(geo, 5.0, String.format("PostalAssociation/%s", slot));
                        postal.markAnchor(); // If not already marked.
                        return true;
                    }
                }
            }
        }
        return false;
    }


    /**
     * For situations of the form:
     * <pre>
     *     CITY  PROV POSTAL
     *     CITY  PROV POSTAL COUNTRY
     *           PROV POSTAL COUNTRY
     *
     *    etc.  where PROV is either name or ADM1 postal code; And POSTAL appears in any order in tuple.
     * </pre>
     * Do the following:
     * (a) generate new span (PlaceCandidate) match
     * (b) set the chosen location to be City or Province whichever is finest resolution.
     * (c) insert new match into original array
     * <p>
     * return super set of all matches.    This makes use of the linkedGeography.
     *
     * @param postalMatches
     * @param t
     * @return all postal matches, now with derived ones added.
     */
    public static List<TextMatch> deriveMatches(List<TextMatch> postalMatches, TextInput t) {

        ArrayList<TextMatch> derived = new ArrayList<>();
        for (TextMatch m : postalMatches) {
            derived.add(m);
            if (!(m instanceof PlaceCandidate) || m.isFilteredOut()) {
                continue;
            }
            PlaceCandidate mention = (PlaceCandidate) m;
            if (mention.isAnchor() && mention.getRelated() != null) {
                PlaceCandidate newSpan = deriveMention(mention, mention.getRelated(), t);
                newSpan.setType(m.getType());
                newSpan.match_id = String.format("%s-derived@%d", m.getType(), newSpan.start);
                derived.add(newSpan);
            } else if (unqualifiedPostalLocation(mention)) {
                // Unpaired Postal code.
                mention.setFilteredOut(true);
            }
        }
        return derived;
    }

    public static boolean unqualifiedPostalLocation(PlaceCandidate match) {
        Place geo = match.getChosenPlace();
        return geo == null || geo.isPostal();
    }

    /**
     * Take a given mention that has related mentions and compose a new expanded mention.
     * for example, given:
     * <pre>
     *    QUE  789123   (postal tagger)
     *    Montreal   xxx  xxxxxx  CANADA  (place tagger)
     *    "Montreal   QUE  789123  CANADA"  (derived mention)
     * </pre>
     * To be clear, this anchors on "postal" codes specifically and expands from there.
     * This will not use the city or province to expand in that direction.
     *
     * @param anchor
     * @param spans
     * @param text
     * @return
     */
    private static PlaceCandidate deriveMention(PlaceCandidate anchor, Collection<PlaceCandidate> spans, TextInput text) {
        PlaceCandidate mention = new PlaceCandidate(anchor.start, anchor.end);
        mention.setDerived(true);

        int x1 = mention.start;
        int x2 = mention.end;
        int confidence = 0;

        // Assemble geolocation first based on prior linked geography.
        //  .... IF for some odd reason this is empty, then fall back on individual spans chosen().
        // Set Geolocation
        mention.setLinkedGeography(anchor.getLinkedGeography());
        if (anchor.getChosen() != null) {
            // Shortcut -- add a previously Scored, chosen place to a new mention.
            mention.linkGeography(VAL_POSTAL, anchor.getChosenPlace());
            mention.addPlace(anchor.getChosen(), 0.0); /* addPlace with incremental score of 0 */
            mention.choose();
        } else if (anchor.getLinkedGeography() != null) {
            for (String knownSlot : PlaceCandidate.KNOWN_GEO_SLOTS) {
                if (anchor.hasLinkedGeography(knownSlot)) {
                    // TODO: Will this ever fire?
                    mention.setChosenPlace(anchor.getLinkedGeography().get(knownSlot));
                    break;
                }
            }
        }

        for (PlaceCandidate m : spans) {
            // TODO: by this point is mention.chosen() null?
            if (m.start < x1) {
                x1 = m.start;
            }
            if (m.end > x2) {
                x2 = m.end;
            }
            if (m.getConfidence() > confidence) {
                confidence = m.getConfidence();
            }
        }

        // Set match text
        mention.start = x1;
        mention.end = x2;
        mention.setText(text.buffer.substring(mention.start, mention.end));

        // Set confidence; Average confidence + 10 points per linked mention.
        mention.setConfidence(confidence + 10 * spans.size());
        for (String rl : anchor.getRules()) {
            mention.addRule(rl);
        }
        mention.addRule("PostalAddressDerivation");

        // Set location
        return mention;
    }
}
