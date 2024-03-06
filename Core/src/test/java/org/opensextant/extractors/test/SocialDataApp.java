package org.opensextant.extractors.test;

import java.io.File;
import java.io.IOException;

import jodd.json.JsonObject;
import org.opensextant.data.social.JSONListener;
import org.opensextant.data.social.MessageParseException;
import org.opensextant.data.social.Tweet;
import org.opensextant.data.social.TweetLoader;
import org.opensextant.util.TextUtils;

public class SocialDataApp implements JSONListener {

    public static void main(String[] args) {

        String file = args[0];
        SocialDataApp tester = new SocialDataApp();
        try {
            TweetLoader.readJSONByLine(new File(file), tester);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public boolean preferJSON() {
        // TODO Auto-generated method stub
        return true;
    }

    @Override
    public void readObject(JsonObject json) throws MessageParseException {
        // JSONObject json = JSONObject.fromObject(obj);
        Tweet tw = new Tweet();
        tw.fromJSON(json);

        tw.setTextNatural(TextUtils.parseNaturalLanguage(tw.getText()));
        System.out.println(tw.toString());
        System.out.println(String.format("\t(%s)", tw.getTextNatural()));
    }

    @Override
    public void readObject(String obj) throws MessageParseException {
        System.out.println("RAW\t" + obj);
    }

    /**
     * Testing only. We don't flag if done or not... its over when its over.
     */
    @Override
    public boolean isDone() {
        return false;
    }

}
