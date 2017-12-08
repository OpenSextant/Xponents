package org.opensextant.extractors.geo.rules;

import java.io.IOException;
import java.util.List;

import org.opensextant.data.Place;
import org.opensextant.extractors.geo.PlaceCandidate;
import org.opensextant.util.GeonamesUtility;

public class ProvinceNameSetter extends GeocodeRule {

    private static GeonamesUtility nameHelper = null;

    /**
     * Configure name helper if you want Province name resolution and other things..
     * @throws IOException
     */
    public ProvinceNameSetter() throws IOException {
        nameHelper = new GeonamesUtility();
        nameHelper.loadWorldAdmin1Metadata();
    }

    protected void assignProvinceName(Place geo) {

        if (geo == null) {
            return;
        }
        if (geo.getCountryCode() == null || geo.getAdmin1() == null) {
            return;
        }

        Place adm1 = nameHelper.getProvince(geo.getCountryCode(), geo.getAdmin1());
        if (adm1 != null) {
            geo.setAdmin1Name(adm1.getName());
        }
    }

    /**
     * Apply a Province name to a chosen place
     */
    public void evaluate(List<PlaceCandidate> names) {
        for (PlaceCandidate pc : names) {
            if (pc.isFilteredOut()) {
                continue;
            }
            /**
             * First choice -- set the place name.
             */
            assignProvinceName(pc.getChosen());
            /**
             * Second choice -- set the place name.
             */
            assignProvinceName(pc.getSecondChoice());
        }
    }

    @Override
    public void evaluate(PlaceCandidate name, Place geo) {
        /* no-op */
    }
}
