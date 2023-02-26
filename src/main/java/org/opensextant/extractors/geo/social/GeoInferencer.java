/*
 * Copyright 2016-2018 The MITRE Corporation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 *
 */
package org.opensextant.extractors.geo.social;

import java.util.Collection;

import org.opensextant.data.social.Tweet;
import org.opensextant.extraction.ExtractionException;
import org.opensextant.extraction.TextMatch;
import org.opensextant.extractors.langid.LangDetect;

/**
 * A geoinferencer infers location on users and their messages.
 * This is a DeepEye-based API where Tweets, Records, and Annotations are the
 * main inputs and outputs.
 *
 * @author ubaldino
 */
public abstract class GeoInferencer extends SocialGeo {

    /**
     * Avg text size (in chars) of tweets -- in 2014, I measured this to be about 90
     * chars. At that
     * tweets with URLs dominate it seems, so actual natural language text avg size
     * may be less.
     */
    public static int AVERAGE_TEXT_SIZE = 90;
    protected LangDetect langidTool = null;
    public long totalRecords = 0;

    /**
     * NOTE: the langID tool from Cybozu can only be loaded once per JVM.
     * So it is initialized once by the data ingester, and then passed in here for
     * use by the processor.
     *
     * @param lid
     */
    public void setLanguageID(LangDetect lid) {
        langidTool = lid;
    }

    /**
     * Infer author's location. Result is a geocoding annotation that contains lat,
     * lon, Country and
     * other gazetteer metadata.
     *
     * @param tw
     *           DeepEye Social Tweet
     * @return annot DeepEye Annotation
     * @throws ExtractionException
     */
    public abstract GeoInference geoinferenceTweetAuthor(Tweet tw) throws ExtractionException;

    /**
     * Infer location of message, if any such metadata is present. Result is a
     * geocoding annotation that contains lat,
     * lon, Country and
     * other gazetteer metadata.
     *
     * @param tw
     *           DeepEye Social Tweet
     * @return inference
     * @throws ExtractionException
     */
    public abstract GeoInference geoinferenceTweetStatus(Tweet tw) throws ExtractionException;

    /**
     * Extract and geocode any mentioned places, countries, coordinates in social
     * media text.
     * For now, this takes a Tweet and uses AUTHOR profile location to help
     * disambiguate found ambiguous tags.
     *
     * @param tw
     * @return
     */
    public abstract Collection<GeoInference> geoinferencePlaceMentions(Tweet tw)
            throws ExtractionException;

    /**
     * If there are by-products of geotagging or inferencing that are worth
     * retrieving,
     * they can be retrieved as "additional matches"
     *
     * @return
     */
    public abstract Collection<TextMatch> getAdditionalMatches();

    protected boolean infersAuthors = false;
    protected boolean infersStatus = false;
    protected boolean infersPlaces = false;

    /** True if your implementation reflects anything off author profile */
    public boolean infersAuthorGeo() {
        return infersAuthors;
    }

    /** True if your implementation reflects anything off status/message */
    public boolean infersStatusGeo() {
        return infersStatus;
    }

    public boolean infersPlaces() {
        return infersPlaces;
    }

    /**
     * Processing report; This could be more structured ala ExtractionMetrics
     * for now this is just a final message from the implementation about general
     * performance.
     *
     * @return
     */
    public abstract String report();

    protected double pct(long tot, long count) {
        return 100 * ((double) count) / tot;
    }

}
