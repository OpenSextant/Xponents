package org.opensextant.extractors.geo.social;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.opensextant.ConfigException;
import org.opensextant.data.Place;
import org.opensextant.data.TextInput;
import org.opensextant.data.social.MessageParseException;
import org.opensextant.data.social.Tweet;
import org.opensextant.extraction.ExtractionException;
import org.opensextant.extraction.MatchFilter;
import org.opensextant.extraction.TextMatch;
import org.opensextant.extractors.geo.PlaceCandidate;
import org.opensextant.extractors.geo.PlaceGeocoder;
import org.opensextant.extractors.xcoord.GeocoordMatch;
import org.opensextant.extractors.xtax.TaxonMatch;
import org.opensextant.processing.Parameters;
import org.opensextant.util.GeonamesUtility;

/**
 * Variant
 * TODO: Ideally, we would chain something like inferredLoc = geocode(Tweet, User, etc) then
 * use the outputs from that to then mentionLocs = geocode(text, given=inferredLoc).
 * 
 * But what a ridiculously intricate pipeline that gets to be,... and you quickly loose the
 * generality of applying this to other data; already heavily Tweet-dependent.
 * 
 */
public class XponentTextGeotagger extends XponentGeocoder {

    public XponentTextGeotagger() {
        inferencerID = "XpGeotag";
        inferencerDescription = "Geotag/geocode Tweet status text, using user metadata for disambiguation where needed";
        infersAuthors = false;
        infersStatus = false;
        infersPlaces = true;
    }

    protected int MATCHCONF_MINIMUM_SOCMEDIA = 10;

    @Override
    public void configure() throws ConfigException {

        //new GazetteerMatcher(true /*allow lowercase tagging*/);
        tagger = new PlaceGeocoder(true);

        tagger.enablePersonNameMatching(true);
        // If you really do not want to miss  anything -- look at this flag:
        // tagger.setAllowLowerCaseAbbreviations(true);
        Parameters xponentsParams = new Parameters();
        // Default Parameters; Unlike geocoding tweet user/status.
        // Default is to tag coords, places, and countries.
        /* "resolve_provinces" is equivalent to setProvinceName(Place) */
        xponentsParams.tag_coordinates = true;
        xponentsParams.resolve_provinces = true;
        tagger.setParameters(xponentsParams);

        /*
         * To filter out trivial places from tweet messages, try adding a mentionFilter file:
         */
        String userFilterPath = "/twitter/exclude-placenames.txt";
        URL filterFile = getClass().getResource(userFilterPath);
        if (filterFile != null) {
            // Add User filter here. This prevents irrelevant stuff from
            // getting out of naiive tagging phase.
            //
            try {
                MatchFilter filt = new MatchFilter(filterFile);
                tagger.setMatchFilter(filt);
            } catch (IOException err) {
                throw new ConfigException("Setup error with geonames utility or other configuration", err);
            }
        } else {
            log.info("Optional user filter not found.  User exclusion list is {}", userFilterPath);
        }

        tagger.configure();

        // Configure tagger first. Then add your custom rules on top of other default rules.
        profileRule = new UserProfileLocationRule();
        tagger.addRule(profileRule);

        try {
            /*
             * Xponents country listing is used throughout.
             */
            countries = new GeonamesUtility();
            countries.loadCountryLanguages();
            countries.loadWorldAdmin1Metadata();

            gazetteer = tagger.getGazetteer();
            this.populateAllCountries(gazetteer);

        } catch (IOException err) {
            throw new ConfigException("IO Problems, possibly missing resource files.", err);
        }
    }

    /**
     * This routine does not look at Author.
     *
     * @param tw Tweet API object
     * @return the annotation with  geo-inference
     * @throws MessageParseException on data parsing error
     * @throws ExtractionException on tagging erorr
     */
    @Override
    public GeoInference geoinferenceTweetAuthor(Tweet tw) throws MessageParseException, ExtractionException {
        return null;
    }

    /**
     * Geotag and Geocode mentions in the message of a tweet.
     * This may make use of Tweet metadata for disambiguation, not just the message content.
     * 
     * @param tw
     *            tweet as parsed by DeepEye. This will use Tweet.lang to direct tagging/tokenization.
     * 
     * @return Geo or Country annotation
     * @throws MessageParseException
     *             on parsing the tweet or JSON
     * @throws ExtractionException
     *             on running geolocation routines
     */
    @Override
    public GeoInference geoinferenceTweetStatus(Tweet tw) throws MessageParseException, ExtractionException {
        return null;
    }

    /**
     * Content-based geotagging. Given the tweet status message, find all place mentions
     * geocoding what is meaningful. Trivial finds are likely omitted or marked with low confidence.
     */
    public Collection<GeoInference> geoinferencePlaceMentions(Tweet tw)
            throws MessageParseException, ExtractionException {
        return processLocationMentions(tw, (Place) tw.authorGeo, tw.id, "geo");
    }

    /**
     * WARNING: Copy of Deepeye Pipes default geotag filter. Adapt this rule/filter to
     * items found in tweets.
     * 
     * @param m
     * @return
     */
    public static boolean filterOut(final PlaceCandidate m) {

        /*
         * Overall, short names that are not classified as abbreviations are
         * almost always false-positives. "ROK" is an unofficial abbreviation
         * for Republic of Korea. Kaw is a very common component of person name
         * in SE Asia... but also an actual place.
         */
        if (m.isUpper() && m.isAbbreviation && m.getLength() >= 3) {
            return false;
        }

        if (m.isLower() /* && m.getText().length() <= 3 && !m.isAbbreviation */) {
            return true;
        }

        return false;
    }

    private List<TextMatch> otherMatches = new ArrayList<>();

    /**
     * This works best if your tweet provides a natural language text, Tweet.setTextNatural()
     */
    public Collection<GeoInference> processLocationMentions(Tweet tw, Place g, String rid, String annotName)
            throws ExtractionException {

        otherMatches.clear();
        this.profileRule.resetBefore(tw, g);

        String text = tw.getTextNatural() != null ? tw.getTextNatural() : tw.getText();
        TextInput data = new TextInput(rid, text);

        /* Your calling pipeline either already did lang-id or you trust the given metadata.
         * Meaningful lang ID:
         * 
         *  lang = 'ar'                    =  Arabic tokenizer/tagging 
         *  lang = 'zh' | 'ja' | 'ko'      =  CJK tokenizer/tagging
         *  lang = 'en' | null | anything else. = Whitespace, default 
         */
        data.langid = tw.lang;

        /*
         * ==========================
         * TAGGING / GEOCODING:
         * ==========================
         * 
         * NOTE:  User Profile Rule is mixed in here to consider how Author Profile Geo
         * (lang/TZ/UTC/Country) are used to adapt the rank of ambiguous places found.
         * 
         */
        List<TextMatch> matches = tagger.extract(data);

        if (matches.isEmpty()) {
            return null;
        }

        List<GeoInference> inferences = new ArrayList<>();
        /*
         * Super loop: Iterate through all found entities. record Taxons as
         * person or orgs record Geo tags as country, place, or geo. geo =
         * geocoded place or parsed coordinate (MGRS, DMS, etc)
         * 
         */
        for (TextMatch name : matches) {

            /*            
             * ==========================
             * ANNOTATIONS: non-geographic entities that are filtered out, but worth tracking
             * ==========================             
             */
            if (name instanceof TaxonMatch) {
                otherMatches.add(name);
                continue;
            }

            /*
             * ==========================
             * FILTERING
             * ==========================
             */
            // Ignore non-place tags
            if (name.isFilteredOut()) {
                continue;
            }
            /*
             * Passed this point ignore other matches.
             */
            if (!(name instanceof PlaceCandidate || name instanceof GeocoordMatch)) {
                continue;
            }

            /*
             * ==========================
             * ANNOTATIONS: coordinates
             * ==========================
             */
            if (name instanceof GeocoordMatch) {
                // coordinate?
                GeocoordMatch geo = (GeocoordMatch) name;
                GeoInference G = new GeoInference();
                G.contributor = inferencerID;
                G.inferenceName = "geo";
                G.geocode = geo;
                G.recordId = rid;
                G.confidence = geo.getConfidence();
                G.start = name.start;
                G.end = name.end;
                inferences.add(G);
                continue;
            }

            /*
             * Other places: Country, Place, or something else that is filterable.
             */
            PlaceCandidate place = (PlaceCandidate) name;
            // Local filtration, based on general noise filtering.
            if (filterOut(place)) {
                log.debug("Filtered out {}", name.getText());
                continue;
            }

            Place resolvedPlace = place.getChosen();
            if (resolvedPlace == null) {
                log.debug("Place Not Resolved {}", name.getText());
                otherMatches.add(name);
                continue;
            }

            /*
             * ==========================
             * ANNOTATIONS: countries, places, etc.
             * ==========================
             */
            /*
             * Accept all country names as potential geotags Else if name can be
             * filtered out, do it now. Otherwise it is a valid place name to consider
             */
            GeoInference G = new GeoInference();
            G.contributor = inferencerID;
            G.recordId = rid;
            G.confidence = place.getConfidence();
            G.geocode = resolvedPlace;
            G.start = name.start;
            G.end = name.end;

            // set inference name so it is clear.
            //  country = Name or Code;  geo = named location with lat/lon;  place = just a name.
            if (place.isCountry) {
                G.inferenceName = "country";
            } else {
                G.inferenceName = place.getConfidence() >= MATCHCONF_MINIMUM_SOCMEDIA ? "geo" : "place";
            }

            inferences.add(G);
        }

        return inferences;
    }

    /**
     * Geotagger does return Additional matches.
     */
    @Override
    public Collection<TextMatch> getAdditionalMatches() {
        return otherMatches;
    }

}
