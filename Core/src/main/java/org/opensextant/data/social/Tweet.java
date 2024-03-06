package org.opensextant.data.social;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;
import org.opensextant.data.Place;
import org.opensextant.util.GeodeticUtility;
import org.opensextant.util.TextUtils;

import jodd.json.JsonArray;
import jodd.json.JsonObject;

public class Tweet extends Message {

    public static String SOURCE_TWITTER = "Twitter";
    public static final String ATTR_ID = "id";
    public static final String ATTR_TEXT = "text";

    public static final String ATTR_SCREEN_NAME = "screen_name";
    public static final String ATTR_AUTH_NAME = "name";
    public static final String ATTR_PROFILE_ID = "profile_id";
    public static final String ATTR_DATE = "created_at";
    public static final String ATTR_EPOCH = "epoch";
    public static final String ATTR_LANG = "lang";
    public static final String ATTR_AUTH_LANG = "ulang";
    public static final String ATTR_GENDER = "gender";
    public static final String ATTR_DESC = "description";
    public static final String ATTR_RETWEET = "rt";
    public static final String ATTR_RETWEET_ID = "rt_id";
    public static final String ATTR_USES_GPS = "gps";
    public static final String ATTR_TZ = "tz";
    public static final String ATTR_UTC_OFFSET = "utc_offset";
    public static final String ATTR_KLOUT = "klout";
    public static final String ATTR_URLS = "urls";
    public static final String ATTR_TAGS = "tags";
    public static final String ATTR_MENTIONS = "mentions";

    public static final DateTimeFormatter timestamp_parser = DateTimeFormat.forPattern("EEE MMM dd HH:mm:ss Z yyyy")
            .withZoneUTC();
    private static final DateTimeFormatter posted_tm_parser = DateTimeFormat.forPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ")
            .withZoneUTC();
    public static final DateTimeFormatter iso_date_formatter = ISODateTimeFormat.dateTime().withZoneUTC();

    public boolean geoEnabled = false;

    public boolean retweet = false;
    public String retweetID = null;

    public String authorDesc = null;

    /** Is TweetStore the main repository of data? */
    protected boolean tweetStore = false;

    protected String entitiesKey = "entities";

    /**
     * M=male, F=female, T=transitional, transgender? Other? Gender is a string,
     * could be an enum, but its no longer two values, I bet.
     */
    public String authorGender = null;

    public Tweet() {
        sourceID = SOURCE_TWITTER;
    }

    public Tweet(String sid) {
        sourceID = sid;
    }

    public Tweet(String _id, String text, Date tm) {
        super(_id, text, tm);
    }

    /**
     * indicate if tweet is geolocated by an accurate coordinate.
     *
     * @return
     */
    public boolean isGeolocated() {
        if (authorGeo != null && authorGeo.isCoordinate()) {
            return true;
        }
        return statusGeo != null && statusGeo.isCoordinate();
    }

    /**
     * indicate if tweet has any Geo resolution at all, wether it was a given
     * Lat/Lon or derived. Careful -- authorGeo and statusGeo are both mutable. If
     * you geocode them directly while still on the Tweet instance, they will look
     * like they were part of the given data.
     *
     * @return
     */
    public boolean isGeoinferenced() {
        if (authorGeo != null && GeodeticUtility.isCoord(authorGeo)) {
            return true;
        }
        return statusGeo != null && GeodeticUtility.isCoord(statusGeo);
    }

    private String geoMethod = null;

    public void setGeoMethod(String m) {
        geoMethod = m;
    }

    /**
     * TODO: this is not clear.
     *
     * @return
     */
    public String getGeoMethod() {
        if (geoMethod != null) {
            return geoMethod; // use your own value.
        }
        if (isGeolocated()) {
            return "LL"; // a given lat/lon
        }
        if (isGeoinferenced()) {
            return "given";
            // various means, either data derived from original
            // data, source/provider GI, or other methods.
        }
        return null;
    }

    public void applyGenericRules() {
        /*
         * A reasonable assumption -- if the text of the language is A, then we can
         * probbably assume the user's language is at least A. But not the other way
         * around. A user may speak/write in multiple languages. userLang <<< lang
         * But we cannot infer userLang >>> lang
         */
        if (userLang == null && lang != null) {
            userLang = lang;
        }

        /*
         * Artifacts of working with JSON serialization, some Array, Object, and
         * NullObject values result in odd string formats, although value may be
         * effectively "null". Possibly user is typing in code or JSON into a text only
         * field. Can't say...
         */
        authorDesc = fixNull(authorDesc);
        authorName = fixNull(authorName);
        // Other nullity issues... in theory every tweet field could be null, but we
        // only care about ones that are plain literals.
        // Major concern is how to serialize on save(), e.g,. when saving to a
        // JSON/noSQL db like mongo.

        /*
         * IFF tags is unset, try to find tags. Otherwise we assume microblog-specific
         * methods already provided all tags.
         */
        if (tags == null) {
            tags = TextUtils.parseHashTags(text);
        }
    }

    public static String fixNull(final String v) {
        if (v != null) {
            if (v.equalsIgnoreCase("[null]")) {
                return null;
            }
        }
        return v;
    }

    /**
     * Set the date and an the standard "CREATED_AT" date/time format.
     *
     * @param d epoch
     */
    public void setDate(long d) {
        this.date = new Date(d);
        this.dateText = Tweet.timestamp_parser.print(d);
    }

    public String getISOTimestamp() {
        return iso_date_formatter.print(date.getTime());
    }

    /**
     * Most commonly needed to parse TweetID from a GnipID
     *
     * @param gnipId
     * @return
     */
    public static String parseId(String gnipId) {
        if (gnipId == null) {
            return null;
        }
        if (gnipId.contains(":")) {
            int x = gnipId.lastIndexOf(":");
            return gnipId.substring(x + 1);
        }
        return gnipId;
    }

    /**
     * Find a best ID from many possible places where ID, id, id_str, etc, reside.
     *
     * @param tw
     * @return
     * @throws MessageParseException
     */
    protected String parseIds(Map<?, ?> tw) throws MessageParseException {
        if (tw.containsKey("id_str")) {
            return (String) tw.get("id_str");
        } else if (tw.containsKey("id")) {
            return parseId((String) tw.get("id"));
        }
        return null;
    }

    /**
     * If "base data" has been filled in by other method, e.g., TW4J or other formal
     * API, then avoid parsing the basics here: id, text ,author*, date, lang, are
     * checked if null. Only if null will they each be found in JSON and parsed.
     * throws MessageParseException
     */
    public void fromJSON(JsonObject tw) throws MessageParseException {
        /*
         *
         */
        Map<String, Object> topLevel = tw.map();
        if (id == null) {
            id = parseIds(topLevel);
            if (id == null) {
                throw new MessageParseException("Could not find a Tweet ID");
            }
        }

        parseText(topLevel);
        parseDate(topLevel);
        parseLanguage(tw.getString("lang"));

        if (lang == null && isASCII) {
            lang = "en";
            isEnglish = true;
        }

        parseAuthor(tw);
        parseRetweet(tw);

        /*
         * Are "mentions" provided in entities, e.g., "entities.user_mentions" Or if
         * data provider has enriched/expanded entities, override parseMentions
         */

        JsonObject ent = tw.getJsonObject(entitiesKey);
        if (isValue(ent)) {
            mentionIDs = parseMentions(ent);
            if (ent.containsKey("urls")) {
                parseURLs(ent.getJsonArray("urls").list());
            }
        }
        if (mentionIDs == null) {
            mentions = parseMentions(text);
        }

        // Such things come from provider or data specific routines.
        // Add to parse utility if and only if not tags are provided.
        // tags = DataUtility.parseHashTags(text);

        setStatusGeo(tw, this);
        setUserGeo(tw, this);

        /*
         * last routine: end with these general data inspection/fix-ups.
         */
        applyGenericRules();
    }

    private void parseRetweet(JsonObject tw) throws MessageParseException {
        retweet = tw.containsKey("retweeted_status");
        if (!retweet) {
            return;
        }

        JsonObject rt = tw.getJsonObject("retweeted_status");

        /*
         * different sources/aggregators of Tweets report retweet flag differently.
         */
        if (isValue(rt)) {
            retweetID = parseIds(rt.map());
            if (retweetID == null) {
                throw new MessageParseException("Could not find a Tweet ID in Retweet");
            }
        } else {
            retweet = false;
        }
    }

    private void parseAuthor(JsonObject tw) {
        if (this.authorID != null) {
            return;
        }
        if (tw.containsKey("user")) {
            setUser(tw.getJsonObject("user"));
        } else if (tw.containsKey("actor")) {
            // Object type is 'person', actually.
            setPerson(tw.getJsonObject("actor"));
        }
    }

    protected void parseDate(Map<?, ?> tw) {
        /*
         * Date string tracking -- argh. Which one really matters dare we normalize one
         * to the other?
         */
        if (date != null) {
            return;
        }
        if (tw.containsKey("postedTime")) {
            dateText = (String) tw.get("postedTime");
            // postedTime=2012-03-23T21:51:47.000Z
            DateTime _d = posted_tm_parser.parseDateTime(dateText);
            date = _d.toDate();
            // Ignore postedTime formatting -- normalize all data to
            // created_at
            dateText = timestamp_parser.print(_d);
        } else if (tw.containsKey("created_at")) {
            dateText = (String) tw.get("created_at");
            // "Wed Oct 10 03:58:28 +0000 2012"
            DateTime _d = timestamp_parser.parseDateTime(dateText);
            date = _d.toDate();
        }

    }

    protected void parseText(Map<?, ?> tw) throws MessageParseException {
        if (this.text != null) {
            // Does not overwrite.
            return;
        }
        if (tw.containsKey("text")) {
            setText((String) tw.get("text"));
        } else if (tw.containsKey("body")) {
            setText((String) tw.get("body"));
        } else {
            throw new MessageParseException("No status text found");
        }

    }

    public void parseLanguage(String lg) {
        if (lang != null) {
            return;
        }
        if (StringUtils.isNotBlank(lg)) {
            setLanguage(lg);
        }
    }

    /**
     * supports gnip.urls or topsy.urls fields
     *
     * @param jsonArray
     */
    public void parseURLs(List<?> jsonArray) {
        if (!Tweet.isValue(jsonArray)) {
            return;
        }

        for (Object obj : jsonArray) {
            // JsonObject url = jsonArray.getJsonObject(x);
            if (obj instanceof Map) {
                Map<?, ?> url = (Map) obj;
                String urlItem = (String) url.get("expanded_url");
                if (StringUtils.isBlank(urlItem)) {
                    urlItem = (String) url.get("url");
                }
                addURL(urlItem);
            }
        }
    }

    public static String LOCATION_FLD = "location";
    public static String COORD_FLD = "coordinates";
    
    /**
     * @param json
     * @param tw
     * @return true if a location metadata was found and set.
     */
    public static boolean setUserGeo(JsonObject json, Tweet tw) {
        JsonObject loc = json.getJsonObject(LOCATION_FLD);
        boolean _loc = isValue(loc);

        boolean _xy = false;
        JsonObject xy = null;
        if (tw.geoEnabled) {
            xy = json.getJsonObject(COORD_FLD);
            _xy = isValue(xy);
        }

        JsonObject geoObj = json.getJsonObject("geo");
        boolean _geo = isValue(geoObj);

        JsonObject pl = json.getJsonObject("place");
        boolean _pl = isValue(pl);

        /*
         * Nothing found.
         */
        if (!_geo && !_loc && !_xy && !_pl) {
            return false;
        }

        /**
         * Found a place of some sort....
         */
        Place p = new Place();

        if (_loc) {
            if (loc.containsKey("displayName")) {
                p.setName(loc.getString("displayName"));
            } else {
                p.setName(loc.toString());
            }
        }
        tw.authorGeo = p;
        if (_xy) {
            setLatLon(p, xy, false);
        } else if (_geo) {
            setLatLon(p, geoObj, true);
        }

        if (_pl) {
            setPlace(p, pl);
        }

        return true;
    }

    /**
     * TODO: investigate how close a user Profile geo compares with Status geo.
     *
     * @param json
     * @param tw
     */
    public static void setStatusGeo(JsonObject json, Tweet tw) {

        JsonObject loc = json.getJsonObject(LOCATION_FLD);
        boolean _loc = isValue(loc);

        boolean _xy = false;
        JsonObject xy = null;
        if (tw.geoEnabled) {
            xy = json.getJsonObject(COORD_FLD);
            _xy = isValue(xy);
        }

        JsonObject geoObj = json.getJsonObject("geo");
        boolean _geo = isValue(geoObj);

        JsonObject pl = json.getJsonObject("place");
        boolean _pl = isValue(pl);

        /*
         * Nothing found.
         */
        if (!_geo && !_loc && !_xy && !_pl) {
            return;
        }

        /**
         * Found a place of some sort....
         */
        Place p = new Place();

        if (_loc) {
            if (loc.containsKey("displayName")) {
                p.setName(loc.getString("displayName"));
            } else {
                p.setName(loc.toString());
            }
        }
        tw.statusGeo = p;
        if (_geo) {
            setLatLon(p, geoObj, true);
        } else if (_xy) {
            setLatLon(p, xy, false);
        }

        if (_pl) {
            setPlace(p, pl);
        }

        /*
         * "geo":{"type":"Point","coordinates":[51.51619,-0.21142]},
         * "place":{"country_code":"GB","url":
         * "http://api.twitter.com/1/geo/id/9fea7e42e33c2145.json","country":
         * "United Kingdom",
         * "place_type":"city","bounding_box":{"type":"Polygon","coordinates":[[
         * [-0.22857,51.47716],[-0.22857,51.53035],[-0.14979,51.53035],[-0.14979
         * ,51.47716]]]}, "full_name":"Kensington and Chelsea, London"
         * ,"attributes":{},"id":"9fea7e42e33c2145","name": "Kensington and Chelsea"}}
         * Gnip Raw:
         * {"type":"Polygon","coordinates":[[[-73.991486,-33.750576],[-32.378185
         * ,-33.750576],[-32.378185,5.27192],[-73.991486,5.27192]]]},
         * "link":"http://api.twitter.com/1/geo/id/1b107df3ccc0aaa1.json",
         * "name":"Brasil", "displayName":"Brasil",
         * "country_code":"Brasil","objectType":"place"}
         */
    }

    /**
     * @param p
     * @param aPlace
     */
    protected static void setPlace(Place p, JsonObject aPlace) {
        p.setName(aPlace.getString("name"));
        p.setFeatureCode(aPlace.getString("place_type"));

        /*
         * When foreign country metadata is used, twitter codes will appear -- so we
         * prefer to use those; or maybe in addition to native lang values.
         */
        if (aPlace.containsKey("twitter_country_code")) {
            p.setCountryCode(aPlace.getString("twitter_country_code"));
        } else {
            p.setCountryCode(aPlace.getString("country_code"));
        }
    }

    /**
     * Order of coordinates is for geo = (LON, LAT) in twitter objects. for
     * coordinates = (LAT, LON) see
     * <a href="http://support.gnip.com/articles/filtering-twitter-data-by-location.html">...</a>
     *
     * @param p
     * @param hasCoord
     * @param latFirst true if type of Point has lat as first field.
     */
    protected static void setLatLon(Place p, JsonObject hasCoord, boolean latFirst) {
        JsonArray ll = hasCoord.getJsonArray(COORD_FLD);
        if (latFirst) {
            p.setLatitude(ll.getDouble(0));
            p.setLongitude(ll.getDouble(1));
        } else {
            p.setLatitude(ll.getDouble(1));
            p.setLongitude(ll.getDouble(0));
        }
    }

    /**
     * @param o
     * @return
     */
    public static boolean isValue(JsonObject o) {
        if (o == null) {
            return false;
        }
        return !o.isEmpty();
        // if o.isNullObject() ?
    }

    public static boolean isValue(List<?> o) {
        return (o != null && !o.isEmpty());
    }

    /**
     * "", null, or "null" checking.
     *
     * @param o
     * @param k
     * @return
     */
    public static String optString(JsonObject o, String k) {
        String v = o.getString(k, null);
        if (isValue(v)) {
            return v;
        }
        return null;
    }

    public static int getInteger(JsonObject o, String k, int defVal) {
        if (o.containsKey(k)) {
            if (o.getValue(k) == null) {
                return defVal;
            }
            return o.getInteger(k, defVal);
        }
        return defVal;
    }

    public static boolean isValue(String o) {
        if (StringUtils.isNotBlank(o)) {
            return !"null".equalsIgnoreCase(o);
        }
        // is Blank... or Java null.
        return false;
    }

    public void setUser(JsonObject tw_user) {
        authorID = tw_user.getString("screen_name");
        authorName = tw_user.getString("name");
        authorLocation = optString(tw_user, LOCATION_FLD);
        authorProfileID = optString(tw_user, "id_str");// Integer.toString(getInteger(tw_user, "id", -1));

        /*
         * Note: this may need to parsed for an actual lat/lon or place name
         * User-defined locations can also be adhoc junk.
         */
        if (authorLocation != null) {
            this.authorGeo = new Place(null, authorLocation);
        }

        if (userLang == null) {
            userLang = tw_user.getString("lang");
        }

        authorDesc = optString(tw_user, "description");

        int off = getInteger(tw_user, "utc_offset", UNSET_UTC_OFFSET);
        setUTCOffset(off);

        timezone = optString(tw_user, "time_zone");

        // For now not available as boolean?
        // geoEnabled = tw_user.optBoolean("geo_enabled");
    }

    public void setPerson(JsonObject tw_user) {
        authorID = tw_user.getString("preferredUsername");
        authorName = tw_user.getString("displayName");
        authorProfileID = parseId(tw_user.getString("id"));
        authorLocation = null;
        if (tw_user.containsKey(LOCATION_FLD)) {
            JsonObject authLocation = tw_user.getJsonObject(LOCATION_FLD);
            this.authorGeo = new Place(null, authLocation.getString("displayName"));
        }

        if (tw_user.containsKey("languages")) {
            JsonArray langs = tw_user.getJsonArray("languages");
            if (langs.size() > 0) {
                userLang = langs.getString(0);
            }
        }

        authorDesc = optString(tw_user, "summary");

        // For now not available as boolean?
        // geoEnabled = tw_user.optBoolean("geo_enabled");
    }

    private Set<String> tags = null;
    private Set<String> urls = null;
    protected Set<String> mentions = null;
    protected List<Mention> mentionIDs = null;

    public static class Mention {
        public String mentionAuthorID = null;
        public String mentionAuthorProfileID = null;
    }

    /**
     * add any tag you like. could be hash tags or entities
     */
    public void addTag(String t) {
        if (tags == null) {
            tags = new HashSet<>();
        }
        tags.add(t);
    }

    public void addURL(String url) {
        if (urls == null) {
            urls = new HashSet<>();
        }
        urls.add(url);
    }

    public Collection<String> getTags() {
        return tags;
    }

    public Collection<String> getURLs() {
        return urls;
    }

    /**
     * Found user screen_names, no user ID
     *
     * @return list of found screen names
     */
    public Collection<String> getMentions() {
        return mentions;
    }

    /**
     * fully qualified Twitter user profiles: screen_name : user ID pairings.
     * Multiple screen names could be associated with the same user ID (although not
     * likley in a single tweet)
     *
     * @return map
     */
    public List<Mention> getMentionIDs() {
        return mentionIDs;
    }

    /**
     * If adding mentions one at a time, then only mention IDS map is used. if
     * profile uid is null, then screen_name, U = null will be mapped
     *
     * @param uname
     * @param uid
     */
    public void addMention(String uname, String uid) {
        if (uname == null && uid == null) {
            return;
        }
        if (mentionIDs == null) {
            mentionIDs = new ArrayList<>();
        }
        Mention m = new Mention();
        m.mentionAuthorID = uname;
        m.mentionAuthorProfileID = uid;
        mentionIDs.add(m);
    }

    static final Pattern twitterIdentity = Pattern.compile("@([\\w\\d_-]+)");

    /**
     * Gets a entities.user_mentions from a normal Tweet.
     *
     * @param entities
     * @return map of screen_name to user profile ID
     */
    public List<Mention> parseMentions(final JsonObject entities) {
        if (!isValue(entities)) {
            return null;
        }
        if (!entities.containsKey("user_mentions")) {
            return null;
        }
        JsonArray arr = entities.getJsonArray("user_mentions");
        if (arr.size() == 0) {
            return null;
        }
        List<Mention> ids = new ArrayList<>();
        for (int x = 0; x < arr.size(); ++x) {
            JsonObject mj = arr.getJsonObject(x);
            Mention m = new Mention();
            m.mentionAuthorID = optString(mj, "screen_name");
            m.mentionAuthorProfileID = optString(mj, "id_str");
            ids.add(m);
        }
        return ids;
    }

    /**
     * From a tweet, get list of "@id"
     *
     * @param msg tweet or other text
     * @return
     */
    public static Set<String> parseMentions(String msg) {
        if (msg == null) {
            return null;
        }
        Matcher m = twitterIdentity.matcher(msg);
        Set<String> ids = null;
        while (m.find()) {
            if (ids == null) {
                ids = new HashSet<>();
            }
            ids.add(m.group());
        }
        return ids;
    }

    @Override
    public String toString() {

        String buf = text +
                " (" +
                id +
                ") " +
                "; Sent at " +
                date.toString() +
                " by " +
                authorName +
                "; Describes self as: " +
                authorDesc;
        return buf;
    }

    public void setLanguage(String l) {
        lang = l;
        if (lang != null) {
            // isEnglish = TextUtils.isEnglish(lang);
            isEnglish = "en".equals(lang);
        }
    }
}
