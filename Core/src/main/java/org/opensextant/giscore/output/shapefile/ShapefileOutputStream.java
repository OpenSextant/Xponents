package org.opensextant.giscore.output.shapefile;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.EmptyStackException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import java.util.zip.ZipOutputStream;

import org.apache.commons.lang3.StringUtils;
import org.opensextant.giscore.IStreamVisitor;
import org.opensextant.giscore.events.ContainerEnd;
import org.opensextant.giscore.events.ContainerStart;
import org.opensextant.giscore.events.Feature;
import org.opensextant.giscore.events.IGISObject;
import org.opensextant.giscore.events.Schema;
import org.opensextant.giscore.events.Style;
import org.opensextant.giscore.events.StyleMap;
import org.opensextant.giscore.output.FeatureKey;
import org.opensextant.giscore.output.FeatureSorter;
import org.opensextant.giscore.output.IContainerNameStrategy;
import org.opensextant.giscore.output.IGISOutputStream;
import org.opensextant.giscore.output.gdb.BasicContainerNameStrategy;
import org.opensextant.giscore.utils.Args;
import org.opensextant.giscore.utils.ObjectBuffer;
import org.opensextant.giscore.utils.ZipUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Output stream for Shape file creation. The basic output routines are lifted
 * from the transfusion mediate package.
 * 
 * @author DRAND
 *
 */
public class ShapefileOutputStream extends ShapefileBaseClass implements IGISOutputStream, IStreamVisitor {
    
	private static final Logger logger = LoggerFactory.getLogger(ShapefileOutputStream.class);
	
	/**
	 * The feature sorter takes care of the details of storing features for
	 * later retrieval by schema.
	 */
	private final FeatureSorter sorter = new FeatureSorter();
	
	/**
	 * Tracks the path - useful for naming collections
	 */
	private final Stack<String> path = new Stack<>();
	
	/**
	 * The first time we find a particular feature key, we store away the 
	 * path and geometry type as a name. Not perfect, but at least it will
	 * be somewhat meaningful.
	 */
	private final Map<FeatureKey, String> datasets = new HashMap<>();
	
	/**
	 * Style id to style map
	 */
	private final Map<String, Style> styles = new HashMap<>();
	
	/**
	 * Style id to specific style. This info is inferred from style map
	 * elements.
	 */
	private final Map<String, String> styleMappings = new HashMap<>();

	/**
	 * Container naming strategy, never null after ctor
	 */
	private final IContainerNameStrategy containerNameStrategy;

	/**
	 * Stream to hold output data
	 */
	private final ZipOutputStream outputStream;

	/**
	 * Place to create the output files. The parent of this must exist.
	 */
	private final File outputPath;

	/**
	 * Maps style icon references to esri shape ids
	 */
	private final PointShapeMapper mapper;

    /**
     * Ctor
     *
     * @param stream                the output stream to write the resulting GDB into, if
     *                              {@code null} then shape files written to directory only.
     * @param path                  the directory and file that should hold the file gdb, never
     *                              {@code null}.
     * @param containerNameStrategy a name strategy to override the default, if
     *                              {@code null} then uses BasicContainerNameStrategy.
     * @param mapper				point to shape mapper
     * @throws IllegalArgumentException if outputStream is not a ZipOutputStream instance nor null  
     */
	public ShapefileOutputStream(OutputStream stream, File path, IContainerNameStrategy containerNameStrategy, PointShapeMapper mapper) {
		this(stream, new Object[]{path, containerNameStrategy, mapper});
	}
	
	/**
	 * Standard ctor
	 * @param stream
	 * @param args
         * @throws IllegalArgumentException if args are invalid
	 */
    public ShapefileOutputStream(OutputStream stream, Object[] args) {
    	if (stream != null) {
            if (!(stream instanceof ZipOutputStream))
    		    throw new IllegalArgumentException("stream must be a zip output stream");
            outputStream = (ZipOutputStream) stream;
    	} else {
    		outputStream = null;
    	}
    	
    	Args argv = new Args(args);
    	File path = (File) argv.get(File.class, 0);
        IContainerNameStrategy containerNameStrategy = (IContainerNameStrategy) argv.get(IContainerNameStrategy.class, 1);
        PointShapeMapper mapper = (PointShapeMapper) argv.get(PointShapeMapper.class, 2);
    	
        if (path == null || path.getParentFile() == null || !path.getParentFile().exists()) {
            throw new IllegalArgumentException(
                    "path should never be null, its parent should never be null and the parent must exist");
        }
        if (containerNameStrategy == null) {
            this.containerNameStrategy = new BasicContainerNameStrategy();
        } else {
            this.containerNameStrategy = containerNameStrategy;
        }    	
        
        outputPath = path;
        this.mapper = mapper != null ? mapper : new PointShapeMapper();
    }
	
	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.opensextant.giscore.output.IGISOutputStream#write(org.opensextant.giscore.events.IGISObject)
	 */
	@Override
	public void write(IGISObject object) {
		object.accept(this);
	}

	@Override
	public void close() throws IOException {
		for(FeatureKey key : sorter.keys()) {
			ObjectBuffer buffer = sorter.getBuffer(key);
			String pathstr = key.getPath();
            List<String> path;
            if (pathstr == null) path = Collections.emptyList();
            else {
                String[] pieces = pathstr.split("_");
                path = Arrays.asList(pieces);
            }
			try {
				String cname = containerNameStrategy.deriveContainerName(path, key);
				Style style = null;
				if (key.getStyleRef() != null) {
					String id = key.getStyleRef();
					if (styleMappings.get(id) != null) {
						id = styleMappings.get(id);
					}
					style = styles.get(id);
				}
				SingleShapefileOutputHandler soh = 
					new SingleShapefileOutputHandler(key.getSchema(), style, buffer, outputPath, cname, mapper);
				soh.process();
			} catch (Exception e) {
				logger.error("Problem reifying data from stream",e);
			}
		}
		sorter.cleanup();
        if (outputStream != null)
		    ZipUtils.outputZipComponents(outputPath.getName(), outputPath, outputStream);
	}

	/**
	 * @exception  EmptyStackException  if {@code ContainerEnd} did not have a preceding <code>ContainerStart</code>
	 */
	@Override
	public void visit(ContainerEnd containerEnd) {
        path.pop();
	}

	/* (non-Javadoc)
	 * @see org.opensextant.giscore.output.StreamVisitorBase#visit(org.opensextant.giscore.events.ContainerStart)
	 */
	@Override
	public void visit(ContainerStart containerStart) {
		path.push(containerStart.getName());
	}

	@Override
	public void visit(Style style) {
		styles.put(style.getId(), style);
	}

    /*
     * (non-Javadoc)
     *
     * @see org.opensextant.giscore.output.StreamVisitorBase#visit(org.opensextant.giscore.events.Schema
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
	 * .Feature)
	 */
	@Override
	public void visit(Feature feature) {
		// Skip non-geometry features
		if (feature.getGeometry() == null) return;
		String fullpath = path != null ? StringUtils.join(path, '_') : null;
		FeatureKey key = sorter.add(feature, fullpath);
		if (datasets.get(key) == null) {
			StringBuilder setname = new StringBuilder();
			setname.append(fullpath);
			if (key.getGeoclass() != null) {
				setname.append("_");
				setname.append(key.getGeoclass().getSimpleName());
			}
			String datasetname = setname.toString();
			datasetname = datasetname.replaceAll("\\s", "_");
			datasets.put(key, datasetname);
		}
	}

	@Override
	public void visit(StyleMap styleMap) {
		String id = styleMap.get(StyleMap.NORMAL);
		if (id != null && id.startsWith("#")) {
			styleMappings.put(styleMap.getId(), id.substring(1));
		}
	}

}
