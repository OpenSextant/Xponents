/****************************************************************************************
 *  PatternContainerNameStrategy.java
 *
 *  Created: May 25, 2010
 *
 *  @author DRAND
 *
 *  (C) Copyright MITRE Corporation 2010
 *
 *  The program is provided "as is" without any warranty express or implied, including
 *  the warranty of non-infringement and the implied warranties of merchantibility and
 *  fitness for a particular purpose.  The Copyright owner will not be liable for any
 *  damages suffered by you as a result of using the Program.  In no event will the
 *  Copyright owner be liable for any special, indirect or consequential damages or
 *  lost profits even if the Copyright owner has been advised of the possibility of
 *  their occurrence.
 *
 ***************************************************************************************/
package org.opensextant.giscore.output.gdb;

import java.text.MessageFormat;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.opensextant.giscore.geometry.Geometry;
import org.opensextant.giscore.output.FeatureKey;

/**
 * A container naming strategy that augments the basic naming strategy by
 * allowing substitution strings based on the geometry of the features.
 * 
 * @author DRAND
 */
public class PatternContainerNameStrategy extends BasicContainerNameStrategy {

    private static final long serialVersionUID = 1L;

	private static final String DEFAULT = "{0}{1}{2}";
	private final Map<Class<? extends Geometry>, String> patterns;
	private final char pathdelim;
	private final String delim;

	/**
	 * The pattern string should be in the form of a
	 * {@link java.text.MessageFormat} where the first argument will be
	 * substituted by the path derived from the container path of the feature.
	 * This is created by taking the container name list and separating them by
	 * the passed delimiter character.
	 * <p>
	 * If there is no pattern string defined then the default is formed by 
	 * using the simple name of the class and appending that to the path using
	 * the same delimiter character. This is nearly the same as the 
	 * behavior of the super class.
	 * <p>
	 * Before the final container name is returned, all whitespace is substituted
	 * with by the delimiter character to avoid programmatic problems with ESRI
	 * software.
	 * 
	 * @param patterns
	 *            patterns to be used when deriving the container name for a
	 *            given geometry, never {@code null} but may be empty.
	 * @param pathdelim the path delimiter character
	 */
	public PatternContainerNameStrategy(
			Map<Class<? extends Geometry>, String> patterns, char pathdelim) {
		super();
		if (patterns == null) {
			throw new IllegalArgumentException("patterns should never be null");
		}
		this.patterns = patterns;
		this.pathdelim = pathdelim;
		char[] delims = new char[1];
		delims[0] = pathdelim;
		delim = new String(delims);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.opensextant.giscore.output.esri.BasicContainerNameStrategy#deriveContainerName
	 * (java.util.List, org.opensextant.giscore.output.FeatureKey)
	 */
	@Override
	public String deriveContainerName(List<String> path, FeatureKey key) {
		String pattern = patterns.get(key.getGeoclass());
		String rval;
		if (pattern == null) {
			rval = MessageFormat.format(DEFAULT, StringUtils.join(path, pathdelim), 
					pathdelim, key.getGeoclass().getSimpleName());
		} else {
			rval = MessageFormat.format(pattern, StringUtils.join(path, pathdelim));
		}
		
		rval = rval.replaceAll("\\s+", delim);
		return rval;
	}

}
