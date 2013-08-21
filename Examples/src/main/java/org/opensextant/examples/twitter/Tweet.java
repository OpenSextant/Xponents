/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.opensextant.examples.twitter;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import net.sf.json.JSONObject;

/**
 *
 * @author ubaldino
 */
public class Tweet extends MicroMessage {

    /**
     * "created_at": "Wed Oct 10 03:58:28 +0000 2012",
     */
    public static SimpleDateFormat tm_parser = new SimpleDateFormat("EEE MMM dd HH:mm:ss Z yyyy");

    public Tweet() {
    }


    public Tweet(String _id, String text, Date tm) {
        super(_id, text, tm);
    }

    @Override
    public void fromJSON(JSONObject tw)
            throws Exception {
        reset();
        /**
         *@see sample.json
         *
         *
         */
        id = tw.getString("id");
        setText(tw.getString("text"));

        // "Wed Oct 10 03:58:28 +0000 2012"
        pub_date = tm_parser.parse(tw.getString("created_at"));

        setUser(tw.getJSONObject("user"));
        setAuthorXY(tw.optJSONObject("geo"));
        setAuthorLocation(tw.optString("location"));
    }

    public void setAuthorXY(JSONObject geo) {
        if (geo == null) {
            return;
        }
        if (geo.containsKey("coordinates")) {
            author_xy_val = geo.getString("coordinates");
        }
    }
    @Override
    public void reset() {
        super.reset();
        if (tags != null) {
            tags.clear();
        }
    }

    public void setAuthorLocation(String desc) {
        author_location = desc;
    }


    public void setUser(JSONObject tw_user) {
        author = tw_user.getString("screen_name");
    }
    private Set<String> tags = null;

    /** add any tag you like.  could be hash tags or entities
     */
    public void addTag(String t) {
        if (tags == null) {
            tags = new HashSet<String>();
        }
        tags.add(t);
    }
}
