package org.opensextant.giscore.output.gdb;

import java.io.Serializable;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.opensextant.giscore.geometry.Geometry;
import org.opensextant.giscore.output.FeatureKey;
import org.opensextant.giscore.output.IContainerNameStrategy;

/**
 * Strategy that uses the current "container" along with the geometry to
 * derive a name.
 */
public class BasicContainerNameStrategy implements
        IContainerNameStrategy, Serializable {
    
	private static final long serialVersionUID = 1L;

	/*
       * (non-Javadoc)
       *
       * @see
       * org.opensextant.giscore.output.IContainerNameStrategy#deriveContainerName
       * (java.util.List, org.opensextant.giscore.output.FeatureKey)
       */
    @Override
	public String deriveContainerName(List<String> path, FeatureKey key) {
        StringBuilder setname = new StringBuilder();

        setname.append(StringUtils.join(path, '_'));
        Class<? extends Geometry> geoclass = key.getGeoclass();
        if (geoclass != null) {
            if (setname.length() > 0)
                setname.append("_");
            setname.append(geoclass.getSimpleName());
        }
        String datasetname = setname.toString();
        datasetname = datasetname.replaceAll("\\s+", "_");

        return datasetname;
    }
}