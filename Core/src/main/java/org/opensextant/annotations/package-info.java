/**
 * 
 * DeepEye is an approach for simplifying typical NLP annotation exchanges. It
 * represents a
 * practical data model for representing annotations -- any span of text tagged
 * with some metadata
 * in the context of a document. The resulting annotation can be serialized as
 * JSON, stored in a
 * database, and later deserialized or retrieved from that database. All of
 * these transformations
 * from Java or object state to representational state incur some loss, some
 * interpretation, etc.
 * DeepEye offers some best practices and some conveniences that support rapid
 * prototyping where NLP
 * is invoked natively or RESTfully and the outputs are persisted in databases.
 * The key concepts are the Record and the Annotation. A Record object
 * represents the original data
 * and any associated metadata. A Record must have an identifier and usually
 * relates to a single
 * source. Annotations are any key-value pair derived from a Record by some
 * processing routine.
 * Data structure:
 * <ul>
 * <li>Records have an id, text, and attributes. Other optional fields, as well,
 * such as processing
 * state to indicate processing was done and annotations were contributed by
 * that processor.</li>
 * <li>Annotations link to Record by rec_id, they have a name, value, offset(s),
 * attributes. As
 * processing may yield spurious, repetitive annotations the AnnotationHelper
 * can be used to cache
 * the same name/value annotation as it appears over many span offsets. This
 * convenience we term as
 * annotation compression.</li>
 * </ul>
 * Additional machinery here helps in pipelines:
 * <ul>
 * <li>AnnotationHelper is a utility class that can be used to formulate common
 * OpenSextant
 * annotations from Java classes. This utility class also helps with annotation
 * compression and
 * distilling large results in memory.</li>
 * <li>DeepEyeStore is a noSQL-style API for finding and updating Records,
 * saving Annotations,
 * updating Annotations and recording Record state. MongoDB, PostgreSQL and
 * SQLite implementations
 * have been attempted, where MongoDB has been the most successful. This class
 * is only an interface
 * specification without implementation.</li>
 * </ul>
 * <pre>
 * IIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIII
 *
 * Xponents sub-project "DeepEye", NLP methodology
 *
 * IIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIII
 * Copyright 2013-2021 MITRE Corporation
 * </pre>
 **/
package org.opensextant.annotations;