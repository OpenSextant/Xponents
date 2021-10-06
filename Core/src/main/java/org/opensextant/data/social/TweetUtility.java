package org.opensextant.data.social;

import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.opensextant.data.Geocoding;
import org.opensextant.data.Place;
import org.opensextant.data.social.Tweet.Mention;
import org.opensextant.util.GeodeticUtility;

import jodd.json.JsonArray;
import jodd.json.JsonObject;

/**
 * @author ubaldino
 */
public class TweetUtility {

    private static final String JSON_NULL = "null";

    public static boolean isValue(String str) {
        return StringUtils.isNotBlank(str) && !JSON_NULL.equalsIgnoreCase(str);
    }

    public static JsonObject toJSON(Tweet tw) {
        return toJSON(tw, true, true);
    }

    /**
     * Objective of this is to create a FLAT key/value map for a JSON or a simple
     * HashMap to use.
     *
     * @param tw         tweet obj
     * @param includeGeo include geo* fields
     * @param formatDate true if date should be in both text and epoch
     * @return JSON
     */
    public static JsonObject toJSON(Tweet tw, boolean includeGeo, boolean formatDate) {

        JsonObject o = new JsonObject();

        o.put("id", tw.id);
        o.put("text", tw.getText());
        o.put("source_id", tw.sourceID);

        // AUTHOR
        o.put("screen_name", tw.authorID);
        if (isValue(tw.authorName)) {
            o.put("name", tw.authorName);
        }
        if (isValue(tw.authorProfileID)) {
            o.put(Tweet.ATTR_PROFILE_ID, tw.authorProfileID);
        }

        /* Epoch will be inserted into all output structs */
        if (formatDate) {
            o.put("created_at", tw.getISOTimestamp());
            o.put("epoch", tw.date.getTime());
        } else {
            o.put("epoch", tw.date.getTime());
        }

        if (isValue(tw.lang)) {
            o.put("lang", tw.lang);
        }
        if (isValue(tw.userLang)) {
            o.put("ulang", tw.userLang);
        }
        if (isValue(tw.authorGender)) {
            o.put("gender", tw.authorGender);
        }
        if (isValue(tw.authorDesc)) {
            o.put("description", tw.authorDesc);
        }
        if (tw.retweet) {
            o.put(Tweet.ATTR_RETWEET, 1);
            o.put(Tweet.ATTR_RETWEET_ID, tw.retweetID);
        }
        if (tw.geoEnabled) {
            o.put("gps", 1);
        }
        if (isValue(tw.timezone)) {
            o.put("tz", tw.timezone);
        }

        /*
         * UTC offset happens to be the only integer value.
         * special "unset" value is used here. Offset is in seconds
         */
        if (tw.utcOffset != Message.UNSET_UTC_OFFSET) {
            o.put("utc_offset", tw.utcOffset);
        }

        if (tw.getURLs() != null) {
            o.put("urls", StringUtils.join(tw.getURLs(), ";"));
        }
        if (tw.getTags() != null) {
            o.put("tags", StringUtils.join(tw.getTags(), ";"));
        }

        /*
         * For this we take either extracted/given other user Mentions
         * or extracted/parsed other user Mentions.
         */
        if (tw.getMentionIDs() != null) {
            JsonArray arr = new JsonArray();
            for (Mention m : tw.getMentionIDs()) {
                insertMention(arr, m);
            }
            o.put("mentions", arr);
        } else if (tw.getMentions() != null) {
            JsonArray arr = new JsonArray();
            for (String m : tw.getMentions()) {
                insertMention(arr, m, null);
            }
            o.put("mentions", arr);
        }

        /*
         * Tweet geo:
         * geo-name
         * geo-cc
         * lat
         * lon
         * User geo:
         * ugeo-name
         * ugeo-cc
         * ulat
         * ulon
         */
        if (includeGeo) {

            insertGeo(o, tw.statusGeo, false);
            insertGeo(o, tw.authorGeo, true);

            /*
             * geo-method = given, GI or LL
             * given
             * GI = geo-inferenced
             * LL = Lat/Lon
             */
            String meth = tw.getGeoMethod();
            if (meth != null) {
                o.put("geo-method", meth);
            }
        }

        return o;
    }

    /**
     * TOOD: validate available data for this schema.
     *
     * @param o
     * @param geo
     * @param isUser
     */
    protected static void insertGeo(JsonObject o, Geocoding geo, boolean isUser) {
        if (geo == null) {
            return;
        }

        // Name of a place or city. If its a fictitious city, it may have other
        // metadata.
        // If only name given is just a country name, then only -cc is saved.
        if (isValue(geo.getPlaceName())) {
            o.put(isUser ? "ugeo-name" : "geo-name", geo.getPlaceName());
        }
        // Country code.
        if (isValue(geo.getCountryCode())) {
            o.put(isUser ? "ugeo-cc" : "geo-cc", geo.getCountryCode());
        }
        // Province name
        if (isValue(geo.getAdminName())) {
            o.put(isUser ? "ugeo-prov" : "geo-prov", geo.getAdminName());
        }
        // ADM1 code - province or state or whatever first level admin boundary is.
        if (isValue(geo.getAdmin1())) {
            o.put(isUser ? "ugeo-adm1" : "geo-adm1", geo.getAdmin1());
        }

        // If data contains a lat, lon pair.
        if (GeodeticUtility.isCoord(geo)) {
            if (isUser) {
                o.put("ulat", geo.getLatitude());
                o.put("ulon", geo.getLongitude());
            } else {
                o.put("lat", geo.getLatitude());
                o.put("lon", geo.getLongitude());
            }
        }
    }

    /**
     * TOOD: validate available data for this schema.
     *
     * @param attrs  given attributes
     * @param isUser if attributes represent user profile
     * @return geocoding object populated with attribute data
     */
    protected static Geocoding getGeo(JsonObject attrs, boolean isUser) {
        if (attrs == null) {
            return null;
        }
        Place pl = new Place();
        boolean hasData = false;
        if (isUser) {
            if (attrs.containsKey("ugeo-name")) {
                pl.setPlaceName(attrs.getString("ugeo-name"));
                hasData = true;
            }
            if (attrs.containsKey("ugeo-cc")) {
                pl.setCountryCode(attrs.getString("ugeo-cc"));
                hasData = true;
            }
            if (attrs.containsKey("ugeo-prov")) {
                // v0.5.x Legacy might be "geo-adm1name" -- which was obscure.
                pl.setAdminName(attrs.getString("ugeo-prov"));
                hasData = true;
            }
            if (attrs.containsKey("ugeo-adm1")) {
                pl.setAdmin1(attrs.getString("ugeo-adm1"));
                hasData = true;
            }
            if (attrs.containsKey("ulat")) {
                pl.setLatitude(attrs.getDouble("ulat"));
                pl.setLongitude(attrs.getDouble("ulon"));
                hasData = true;
            }
        } else {

            if (attrs.containsKey("geo-name")) {
                pl.setPlaceName(attrs.getString("geo-name"));
                hasData = true;
            }
            if (attrs.containsKey("geo-cc")) {
                pl.setCountryCode(attrs.getString("geo-cc"));
                hasData = true;
            }
            if (attrs.containsKey("geo-prov")) {
                // v0.5.x Legacy might be "geo-adm1name" -- which was obscure.
                pl.setAdminName(attrs.getString("geo-prov"));
                hasData = true;
            }
            if (attrs.containsKey("geo-adm1")) {
                pl.setAdmin1(attrs.getString("geo-adm1"));
                hasData = true;
            }
            if (attrs.containsKey("lat")) {
                pl.setLatitude(attrs.getDouble("lat"));
                pl.setLongitude(attrs.getDouble("lon"));
                hasData = true;
            }
        }

        if (hasData) {
            return pl;
        }
        return null;
    }

    /**
     * Serialize a user screen name / ID pairing., e.g. a mention of a friend.
     * If just screen name or ID is available, then each item is added separately.
     *
     * @param a  target array
     * @param u  user name
     * @param id mention
     */
    public static void insertMention(final JsonArray a, final String u, final String id) {
        Map<String, String> mention = new HashMap<>();
        if (u != null) {
            mention.put("screen_name", u);
        }
        if (id != null) {
            mention.put(Tweet.ATTR_PROFILE_ID, id);
        }
        if (!mention.isEmpty()) {
            a.add(mention);
        }
    }

    /**
     * Variation on insertMention(a, name, uid)
     */
    public static void insertMention(final JsonArray a, Mention m) {
        Map<String, String> mention = new HashMap<>();
        if (m.mentionAuthorID != null) {
            mention.put("screen_name", m.mentionAuthorID);
        }
        if (m.mentionAuthorProfileID != null) {
            mention.put(Tweet.ATTR_PROFILE_ID, m.mentionAuthorProfileID);
        }
        if (!mention.isEmpty()) {
            a.add(mention);
        }
    }
}
