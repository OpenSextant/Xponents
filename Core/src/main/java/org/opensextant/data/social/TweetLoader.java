package org.opensextant.data.social;

import static org.apache.commons.lang3.StringUtils.isBlank;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;

import org.opensextant.util.FileUtility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jodd.json.JsonObject;
import jodd.json.JsonParser;

public class TweetLoader {
    /**
     * The internals of parsing a JSON file of tweets.
     * You provide the receiver logic using JSONListener:
     *
     * <pre>
     *    myListener = JSONListener(){  readObject( String or JSON map); }
     *
     *    TweetLoader.readJSONByLine(file, myListener)
     * </pre>
     */

    public static int MAX_ERROR_COUNT = 100;

    /**
     * To read gzip/JSON files one row of JSON at a time.
     * This will tolerate up to MAX_ERROR_COUNT for parsing data files...
     *
     * @param jsonFile
     * @param ingester
     * @throws IOException
     */
    public static void readJSONByLine(File jsonFile, JSONListener ingester) throws IOException {

        if (!jsonFile.exists()) {
            throw new IOException("File does not exist; not opening...");
        }
        //BufferedReader reader = null;
        Logger log = LoggerFactory.getLogger(TweetLoader.class);
        int errors = 0;
        try (BufferedReader reader = new BufferedReader(FileUtility.getInputStreamReader(jsonFile, "UTF-8"))){
            JsonParser jsonp = JsonParser.create();
            String line;
            while ((line = reader.readLine()) != null) {
                if (isBlank(line)) {
                    // Eat up ^M (\r) or other whitespace.
                    continue;
                }
                /*
                 * control logic. If reader/ingester is done, the close up stream and exit.
                 */
                if (ingester.isDone()) {
                    break;
                }

                try {
                    /*
                     * JSON or Text.
                     */
                    if (ingester.preferJSON()) {
                        JsonObject obj = jsonp.parseAsJsonObject(line);
                        ingester.readObject(obj);
                    } else {
                        ingester.readObject(line);
                    }
                } catch (Exception someErr) {
                    ++errors;
                    log.error("Ignoring record error ERR={}", someErr.getMessage());
                    log.debug("line failed=" + line, someErr);

                    if (errors > MAX_ERROR_COUNT) {
                        throw new IOException("Exceeded max errors,... Exiting read", someErr);
                    }
                }
            }
        }
    }
}
