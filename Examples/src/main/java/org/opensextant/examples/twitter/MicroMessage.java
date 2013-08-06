/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.opensextant.examples.twitter;

//import java.text.DateFormat;
import java.io.File;
import java.util.Date;
import java.util.Map;
import net.sf.json.JSONObject;
import org.opensextant.data.LatLon;

/**
 * TODO: create a better common data class.
 *
 * @author ubaldino
 */
public abstract class MicroMessage {

    /**
     * NewsItem -- override later
     */
    //public String body = null;
    //public long rawbytes = 0L;
    public String author = null;
    public String location_cc = null;
    public String author_cc = null;
    public String author_location = null;
    public LatLon author_xy = null;

    public MicroMessage() {
    }

    public abstract void fromJSON(JSONObject data) throws Exception;

    public MicroMessage(String _id, String text, Date tm) {

        this.id = _id;
        this.setBody(text);
        this.pub_date = tm;
    }

    public void reset() {
        id = null;
        body = null;
        rawbytes = 0;
        location_cc = null;
        author_cc = null;
        author_location = null;
        author = null;
        pub_date = null;
    }

    /**
     * NewsItem -- override later
     */
    public final void setBody(String t) {
        body = t;
        if (t != null) {
            rawbytes = t.length();
        } else {
            rawbytes = 0L;
        }
    }

    /**

     */
    public String getBody() {
        return this.body;
    }

    
        /** Data attributes */
    public Date pub_date = null;
    public String id = null;
    public String subject = null;
    /** Integration attributes */
    protected String body = null;
    public long rawbytes = 0L;  // This is really Char Count.


}
