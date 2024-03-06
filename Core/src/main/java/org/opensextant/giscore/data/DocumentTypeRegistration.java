/****************************************************************************************
 *  DocumentTypeRegistration.java
 *
 *  Created: May 2, 2013
 *
 *  @author DRAND
 *
 *  (C) Copyright MITRE Corporation 2013
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
package org.opensextant.giscore.data;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Constructor;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import org.opensextant.giscore.DocumentType;
import org.opensextant.giscore.input.IGISInputStream;
import org.opensextant.giscore.output.IGISOutputStream;

/**
 * Stores the registration information for a given pair of an input and output
 * stream and a document type. Either the input or output stream can be missing,
 * in which case that entry will be ignored for the purposes of the factory.
 * 
 * This class is not used as a key, so there's no immediate reason to define
 * hashCode or equals.
 * 
 * @author DRAND
 *
 */
@SuppressWarnings("rawtypes") 
public class DocumentTypeRegistration {

	private final DocType type;
	private Class inputStreamClass;
	private Class[] inputStreamArgs = new Class[0];
	private boolean[] inputStreamArgsRequired = new boolean[0];
	private Class outputStreamClass;
	private Class[] outputStreamArgs = new Class[0];
	private boolean[] outputStreamArgsRequired = new boolean[0];
	private boolean hasFileCtor = false;
	
	/**
	 * Empty ctor
	 */
	public DocumentTypeRegistration(DocumentType type) {
		if (type == null) {
			throw new IllegalArgumentException(
					"type should never be null");
		}
		this.type = type.getDocType();
	}
	
	/**
	 * Empty ctor
	 */
	public DocumentTypeRegistration(DocType type) {
		if (type == null) {
			throw new IllegalArgumentException(
					"type should never be null");
		}
		this.type = type;
	}

	/**
	 * @return the type
	 */
	public DocType getType() {
		return type;
	}

	/**
	 * @return the inputStreamArgs
	 */
	public Class[] getInputStreamArgs() {
		return inputStreamArgs;
	}

	/**
	 * @param inputStreamArgs the inputStreamArgs to set
	 */
	public void setInputStreamArgs(Class[] inputStreamArgs) {
		this.inputStreamArgs = inputStreamArgs;
	}

	/**
	 * @return the outputStreamArgs
	 */
	public Class[] getOutputStreamArgs() {
		return outputStreamArgs;
	}

	/**
	 * @param outputStreamArgs the outputStreamArgs to set
	 */
	public void setOutputStreamArgs(Class[] outputStreamArgs) {
		this.outputStreamArgs = outputStreamArgs;
	}

	/**
	 * @return the inputStreamClass
	 */
	public Class getInputStreamClass() {
		return inputStreamClass;
	}

	/**
	 * @param inputStreamClass the inputStreamClass to set
	 */
	public void setInputStreamClass(Class inputStreamClass) {
		this.inputStreamClass = inputStreamClass;
	}

	/**
	 * @return the outputStreamClass
	 */
	public Class getOutputStreamClass() {
		return outputStreamClass;
	}

	/**
	 * @param outputStreamClass the outputStreamClass to set
	 */
	public void setOutputStreamClass(Class outputStreamClass) {
		this.outputStreamClass = outputStreamClass;
	}

	/**
	 * @return the inputStreamArgsRequired
	 */
	public boolean[] getInputStreamArgsRequired() {
		return inputStreamArgsRequired;
	}

	/**
	 * @param inputStreamArgsRequired the inputStreamArgsRequired to set
	 */
	public void setInputStreamArgsRequired(boolean[] inputStreamArgsRequired) {
		this.inputStreamArgsRequired = inputStreamArgsRequired;
	}

	/**
	 * @return the outputStreamArgsRequired
	 */
	public boolean[] getOutputStreamArgsRequired() {
		return outputStreamArgsRequired;
	}

	/**
	 * @param outputStreamArgsRequired the outputStreamArgsRequired to set
	 */
	public void setOutputStreamArgsRequired(boolean[] outputStreamArgsRequired) {
		this.outputStreamArgsRequired = outputStreamArgsRequired;
	}

	/**
	 * @return the hasFileCtor
	 */
	public boolean hasFileCtor() {
		return hasFileCtor;
	}

	/**
	 * @param hasFileCtor the hasFileCtor to set
	 */
	public void setHasFileCtor(boolean hasFileCtor) {
		this.hasFileCtor = hasFileCtor;
	}
	
	/**
	 * Check the count and types of the arguments against the required types
	 * 
	 * @param input true for input, false for output
	 * @param arguments the arguments
	 * required for each argument, is it required? If required has fewer
	 *            elements than types or arguments the remainder is treated as
	 *            false. If a {@code false} element is found, any further
	 *            {@code true} value is ignored, i.e. only leading
	 *            arguments are considered required.
	 */
	@SuppressWarnings("unchecked")
	public void checkArguments(boolean input, Object[] arguments) {
		boolean[] required = input ? inputStreamArgsRequired : outputStreamArgsRequired;
		Class[] types = input ? inputStreamArgs : outputStreamArgs;
		
		int nreq = 0;
		for (boolean aRequired : required) {
			if (!aRequired)
				break;
			nreq++;
		}
		if (arguments.length < nreq) {
			throw new IllegalArgumentException(
					"There are insufficient arguments, there should be at least "
							+ types.length);
		}
		for (int i = 0; i < arguments.length; i++) {
			boolean argreq = false;
			Class<? extends Object> type = Object.class;
			Object arg = arguments[i];
			if (i < required.length) {
				argreq = required[i];
			}
			if (i < types.length) {
				type = types[i];
			}
			if (arg == null && argreq) {
				throw new IllegalArgumentException("Missing argument " + i);
			}
			if (arg != null) {
				Class<? extends Object> argtype = arg.getClass();
				if (!type.isAssignableFrom(argtype)) {
					throw new IllegalArgumentException("Argument #" + i
							+ " should be of a class derived from " + type);
				}
			}
		}
	}
	
	@SuppressWarnings("unchecked")
	private Object findAndInvokeCtor(Object first, Object[] rest) throws InstantiationException {
		try {
			Class firstarg = null;
			Class baseclass = null;
			if (first instanceof InputStream || first instanceof ZipInputStream) {
				firstarg = java.io.InputStream.class;
				baseclass = inputStreamClass;
			} else if (first instanceof OutputStream || first instanceof ZipOutputStream) {
				firstarg = java.io.OutputStream.class;
				baseclass = outputStreamClass;
			} else if (first instanceof java.io.File) {
				firstarg = java.io.File.class;
				baseclass = inputStreamClass;
			} else {
				throw new InstantiationException("First argument wasn't a stream or file");
			}
			Constructor ctor = baseclass.getConstructor(firstarg, Object[].class);
			return ctor.newInstance(first, rest);
		} catch (Exception e) {
			throw new InstantiationException("Couldn't create stream");
		}
	}
	
	

	public IGISInputStream getInputStream(InputStream stream, Object[] arguments) throws InstantiationException {
		return (IGISInputStream) findAndInvokeCtor(stream, arguments);
	}

	

	public IGISInputStream getInputStream(File file, Object[] arguments) throws InstantiationException {
		return (IGISInputStream) findAndInvokeCtor(file, arguments);
	}

	public IGISOutputStream getOutputStream(OutputStream outputStream,
			Object[] arguments) throws InstantiationException {
		return (IGISOutputStream) findAndInvokeCtor(outputStream, arguments);
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		return "DocumentTypeRegistration [type=" + type.name() + ", inputStreamClass="
				+ inputStreamClass.getCanonicalName() + ", outputStreamClass=" + outputStreamClass.getCanonicalName()
				+ "]";
	}
}
