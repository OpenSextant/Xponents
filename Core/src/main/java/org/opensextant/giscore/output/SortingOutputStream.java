/****************************************************************************************
 *  SortingOutputStream.java
 *
 *  Created: Apr 16, 2009
 *
 *  @author DRAND
 *
 *  (C) Copyright MITRE Corporation 2009
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
package org.opensextant.giscore.output;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.opensextant.giscore.ICategoryNameExtractor;
import org.opensextant.giscore.events.*;
import org.opensextant.giscore.utils.ObjectBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This is an output stream that takes information in the Rows or Row subclasses
 * handed to it in order to place them in different containers. It will reorder
 * the data by using a feature sorter to hold data for each collection. On close
 * it will call the actual output stream and output the features in one
 * container per category. 
 * <p>
 * To use this stream, open a gis output stream and create this as a wrapper. 
 * Note that other kinds of features should be sent directly to the original 
 * stream, which is available via the {@link #getInnerStream()} call as well
 * as by saving a reference in the caller. It is the callers responsibility to
 * call the close method on the original stream.
 * 
 * @author DRAND
 * 
 */
public class SortingOutputStream extends StreamVisitorBase implements
		IGISOutputStream {
	private static final Logger logger = LoggerFactory.getLogger(SortingOutputStream.class);
	
	/**
	 * The feature sorter
	 */
	private final FeatureSorter sorter = new FeatureSorter();

	/**
	 * The gis output stream, assigned in the ctor and never changed afterward.
	 * This is never {@code null}.
	 */
	private final IGISOutputStream stream;

	/**
	 * The name strategy to use for creating containers, assigned in the ctor
	 * and never changed afterward. This is never {@code null}.
	 */
	private final IContainerNameStrategy strategy;

	/**
	 * Tracks the path - useful for naming collections
	 */
	private final List<String> path = new ArrayList<>();

	/**
	 * The extractor that will determine the name of a category based on the
	 * actual data in the row or row subclass.
	 */
	private final ICategoryNameExtractor extractor;
	
	/**
	 * Ctor
	 * 
	 * @param innerstream
	 * @param strategy
	 */
	public SortingOutputStream(IGISOutputStream innerstream,
			IContainerNameStrategy strategy, ICategoryNameExtractor extractor) {
		if (innerstream == null) {
			throw new IllegalArgumentException("innerstream should never be null");
		}
		if (strategy == null) {
			throw new IllegalArgumentException("strategy should never be null");
		}
		if (extractor == null) {
			throw new IllegalArgumentException("extractor should never be null");
		}
		this.stream = innerstream;
		this.strategy = strategy;
		this.extractor = extractor;
	}
	
	
	/**
	 * @return get the wrapped stream, useful for outputting non-sorted bits
	 * and pieces. Note that anything output this way will appear <em>before</em>
	 * the features that have been sorted.
	 */
	public IGISOutputStream getInnerStream() {
		return stream;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.opensextant.giscore.output.IGISOutputStream#write(org.opensextant.giscore.events
	 * .IGISObject)
	 */
	@Override
	public void write(IGISObject object) {
		object.accept(this);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.opensextant.giscore.output.StreamVisitorBase#visit(org.opensextant.giscore.events
	 * .ContainerEnd)
	 */
	@Override
	public void visit(ContainerEnd containerEnd) {
		if (!path.isEmpty()) {
			path.remove(path.size() - 1);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.opensextant.giscore.output.StreamVisitorBase#visit(org.opensextant.giscore.events
	 * .ContainerStart)
	 */
	@Override
	public void visit(ContainerStart containerStart) {
		if (StringUtils.isNotBlank(containerStart.getName())) {
			path.add(containerStart.getName());
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.opensextant.giscore.output.StreamVisitorBase#visit(org.opensextant.giscore.events
	 * .DocumentStart)
	 */
	@Override
	public void visit(DocumentStart documentStart) {
		stream.write(documentStart);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.opensextant.giscore.output.StreamVisitorBase#visit(org.opensextant.giscore.events
	 * .Feature)
	 */
	@Override
	public void visit(Feature feature) {
		visit((Row) feature);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.opensextant.giscore.output.StreamVisitorBase#visit(org.opensextant.giscore.events
	 * .Row)
	 */
	@Override
	public void visit(Row row) {
		String category = extractor.extractCategoryName(row);
		String fullpath;
		if (!path.isEmpty()) {
			fullpath = StringUtils.join(path, '_') + "_" + category;
		} else {
			fullpath = category;
		}
		sorter.add(row, fullpath);
	}
	
	

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.opensextant.giscore.output.StreamVisitorBase#visit(org.opensextant.giscore.events
	 * .Schema)
	 */
	@Override
	public void visit(Schema schema) {
		sorter.add(schema);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.opensextant.giscore.output.StreamVisitorBase#visit(org.opensextant.giscore.events
	 * .Style)
	 */
	@Override
	public void visit(Style style) {
		stream.write(style);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.opensextant.giscore.output.StreamVisitorBase#visit(org.opensextant.giscore.events
	 * .StyleMap)
	 */
	@Override
	public void visit(StyleMap styleMap) {
		stream.write(styleMap);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.io.Closeable#close()
	 */
	@Override
	public void close() throws IOException {
		Collection<FeatureKey> keys = sorter.keys();
		for(Schema schema : sorter.schemata()) {
			stream.write(schema);
		}
		for(FeatureKey key : keys) {
			ObjectBuffer buffer = sorter.getBuffer(key);
			String pathstr = key.getPath();
            List<String> path;
            if (pathstr == null) path = Collections.emptyList();
            else {
                String[] pieces = pathstr.split("_");
                path = Arrays.asList(pieces);
            }
			try {
				ContainerStart cs = new ContainerStart("Folder");
				cs.setName(strategy.deriveContainerName(path, key));
				stream.write(cs);
				IGISObject obj = (IGISObject) buffer.read();
				do {
					stream.write(obj);
					obj = (IGISObject) buffer.read();
				} while(obj != null);
				ContainerEnd ce = new ContainerEnd();
				stream.write(ce);
			} catch (Exception e) {
				logger.error("Problem reifying data from stream",e);
			}
		}
		sorter.cleanup();
	}

}
