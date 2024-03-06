package org.opensextant.giscore.events;

import java.io.Serializable;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.opensextant.giscore.IStreamVisitor;

/**
 * Represents a Comment object. In XML would
 * be represented as {@code <!-- text content -->}.
 * 
 * @author Jason Mathews, MITRE Corp.
 * Created: Mar 10, 2009 9:03:48 AM
 */
public class Comment implements IGISObject, Serializable {

	private static final long serialVersionUID = 1L;

	private String text;

	/**
     * Default, no-args constructor for implementations to use if needed.
     */
    protected Comment() {
        // empty constructor
    }

	/**
	 * This creates the comment with the supplied text.
     *
     * @param text {@code String} content of comment.
	 */
	public Comment(String text) {
		setText(text);
	}

	/**
	 * This returns the textual data within the {@code Comment}.
     *
     * @return the text of this comment
     */
	public String getText() {
		return text;
	}

	/**
	 * This will set the value of the {@code Comment}.
     *
     * @param text {@code String} text for comment.
	 */
	public void setText(String text) {
		this.text = text;
	}

	/**
	 * Visit the object
	 *
	 * @param visitor the visitor to dispatch to, never {@code null}
	 */
	public void accept(IStreamVisitor visitor) {
		visitor.visit(this);
	}

    /*
	 * (non-Javadoc)
	 *
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj) {
		return EqualsBuilder.reflectionEquals(this, obj);
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see java.lang.Object#hashCode()
	 */
	@Override
	public int hashCode() {
		return HashCodeBuilder.reflectionHashCode(this);
	}

	/**
	 * This returns a {@code String} representation of the
	 * {@code Comment}, suitable for debugging.
	 *
	 * @return {@code String} - information about the
	 *         {@code Comment}
	 */
	public String toString() {
		return "[Comment: " + text + ']';
	}

}
