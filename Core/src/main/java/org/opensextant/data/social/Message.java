package org.opensextant.data.social;

import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTimeZone;
import org.opensextant.data.Geocoding;
import org.opensextant.util.TextUtils;

import java.util.Date;

/**
 * Micro Message is the basis for any sort microblog message, SMS, or tweet or
 * other social media post.
 * These attributes have some conventions:
 * <ul>
 * <li>Author data (author* attributes) are synonmous with User Profile, Poster,
 * etc.
 * Otherwise the attribute is more related to the message itself.
 * </li>
 * <li>This structure represents data which maybe given or raw; or which may be
 * processed or inferred.
 * it is up to the developer to decide how to navigate the differences between
 * such differences.
 * </li>
 * </ul>
 *
 * @author ubaldino
 */
public class Message {

    /**
     * Author ID is screen_name, user_id, user.name, etc.
     */
    public String authorID = null;
    /**
     * A plain language Name, display name, native language name for the author
     */
    public String authorName = null;
    /**
     * the numeric ID for a user/author profile. As users are able to change display
     * names at-will.
     */
    public String authorProfileID = null;
    /**
     * raw country code
     */
    public String authorCC = null;
    /**
     * raw location string
     */
    public String authorLocation = null;
    /**
     * raw XY val, if present on author profile.
     */
    public String authorLatLonText = null;

    /**
     * Location Country Code if inferred or given.
     */
    public String locationCC = null;

    /**
     * Author's profile location -- If country and actual location is set,
     * use Geocoding object, or opensextant.Place to capture the full metadata.
     */
    public Geocoding authorGeo = null;
    /**
     * the origination of the message -- Country from which the item was sent.
     * If the location is set also as a coordinate use that.
     */
    public Geocoding statusGeo = null;

    /* Data attributes */
    /**
     * Date object for the message timestamp
     */
    public Date date = null;
    /**
     * Original text of the date, if given
     */
    public String dateText = null;
    /**
     * Timezone label of the timestamp, e.g., Europe/London, or just London
     * or "Canada and Atlantic (EST)". There are some standard labels, but these
     * labels
     * do not always line up with well-known TZ databases.
     */
    public String timezone = null;
    public static final int UNSET_UTC_OFFSET = 999999;
    /**
     * UTC offset in seconds (as given by Twitter and other sources).
     * Default is UNSET_UTC_OFFSET
     */
    public int utcOffset = UNSET_UTC_OFFSET;
    /**
     * isDST = is Daylight Savings Time = true if we think the message timestamp is
     * in a
     * period of DST recognized by the country of origin, or UTC offset.
     */
    public boolean isDST = false;
    /**
     * utcOffset is in seconds, UTC offset hours is in hours. Duh.
     * Default is UNSET_UTC_OFFSET
     */
    public int utcOffsetHours = utcOffset;

    /**
     * Message ID
     */
    public String id = null;

    /**
     * The text of the message.
     */
    protected String text = null;
    /**
     * The natural language version of the text.
     */
    protected String textNatural = null;

    /**
     * Character count of the message. Or if you choose to store byte count....
     */
    public long rawbytes = 0L;

    /**
     * Language declared by the user.
     */
    public String userLang = null;
    /**
     * Language of the message; not usually given unless inferred by someone or
     * something.
     */
    public String lang = null;
    /**
     * True if text is purely ASCII
     */
    public boolean isASCII = false;
    /**
     * True if processing of text determines tweet is nearly all English.
     */
    public boolean isEnglish = false;

    /** optional Source ID field */
    public String sourceID = null;

    /**
     * Trivial constructor.
     */
    public Message() {
    }

    /**
     * A simple message with an ID, text and a timestamp.
     *
     * @param _id  ID
     * @param text message
     * @param tm   timestamp
     */
    public Message(String _id, String text, Date tm) {
        this.id = _id;
        this.setText(text);
        this.date = tm;
    }

    /**
     * Set the text, if not null, isASCII and rawbytes length are calculated.
     */
    public final void setText(String t) {
        text = t;
        if (t != null) {
            rawbytes = t.length();
            isASCII = TextUtils.isASCII(t);
        } else {
            rawbytes = 0L;
        }
    }

    /**
     * get the message text.
     */
    public String getText() {
        return this.text;
    }

    /**
     * Get the natural language version of the raw text.
     *
     * @return
     */
    public String getTextNatural() {
        return this.textNatural;
    }

    public void setTextNatural(String t) {
        this.textNatural = t;
    }

    /**
     * Set UTC and TZ after date is set.
     *
     * @param utc UTC offset in SECONDS
     */
    public void setUTCOffset(int utc) {
        utcOffset = utc;
        if (utcOffset != UNSET_UTC_OFFSET) {
            // Same as TZ usually.
            DateTimeZone z = DateTimeZone.forOffsetMillis(1000 * utcOffset);
            isDST = !z.isStandardOffset(date.getTime());
            utcOffsetHours = utcOffset / 3600;
        }
    }

    public static final int OFFSET_HALF = 86400 / 2;

    public static boolean validateUTCOffset(int o) {
        return Math.abs(o) < OFFSET_HALF;
    }

    /**
     * UTC offset as hours. Convert from seconds
     *
     * @param o
     * @return
     */
    public static double toUTCOffsetHours(int o) {
        return (double) o / 3600;
    }

    /**
     * detect if Tweet has UTC offset or TZ
     *
     * @param t tweet obj
     * @return
     */
    public static boolean validTZ(Tweet t) {
        if (StringUtils.isNotBlank(t.timezone)) {
            return true;
        }
        // avoid the math if we know this is a default.
        if (t.utcOffset == Message.UNSET_UTC_OFFSET) {
            return false;
        }
        return validateUTCOffset(t.utcOffset);
    }
}
