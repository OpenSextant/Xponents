/*
 * IIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIII
 *
 * OpenSextant/Xponents sub-project
 *      __
 *  ___/ /___  ___  ___  ___  __ __ ___
 * / _  // -_)/ -_)/ _ \/ -_)/ // // -_)
 * \_,_/ \__/ \__// .__/\__/ \_, / \__/
 *               /_/        /___/
 *
 * IIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIII
 * Copyright 2013, 2019 MITRE Corporation
 */
package org.opensextant.annotations;

import java.util.Collection;

/**
 * DeepEyeStore is an abstraction of a data store that stores records and
 * annotations.
 * For example, we built annotation stores implementing DeepEyeStore using
 * MongoDB, Postgres, Elasticsearch and SQLite backends.
 *
 * @author ubaldino
 */
public interface DeepEyeStore {

    public abstract void connect() throws DeepEyeException;

    public abstract void disconnect();

    /**
     * Add new record.
     */
    public abstract void add(Record R) throws DeepEyeException;

    /**
     * The implementation of a record update
     *
     * @param R record to update
     * @throws DeepEyeException on failure to update Record
     */
    public abstract void update(Record R) throws DeepEyeException;

    /**
     * Suggested Save operation: check if exists, update if it does, otherwise
     * insert. This is typically
     * adjustable behavior.
     *
     * @param R
     * @throws DeepEyeException on failure to save Record, because system was down,
     *                          lack of privileges,
     *                          or inability to overwrite existing.
     */
    public abstract void save(Record R) throws DeepEyeException;

    /** Update only the state of a record */
    public abstract void updateState(Record R) throws DeepEyeException;

    /**
     * find a single record. Avoid the overhead of a cursor for "findOne" query.
     * Where you know you are
     * looking for one or none.
     *
     * @param id
     * @return Deepeye Record object
     */
    public abstract Record findRecord(String id) throws DeepEyeException;

    /**
     * add a single Annotation.
     */
    public abstract void add(Annotation A) throws DeepEyeException;

    /**
     * add a list of entity annotations.
     *
     * @param aList
     * @throws DeepEyeException
     */
    public abstract void add(Collection<Annotation> aList) throws DeepEyeException;

    /**
     * Update an existing annotation. This does nothing if the annotation does not
     * exist in the
     * database.
     */
    public abstract void update(Annotation A) throws DeepEyeException;

    /**
     * given the shell of a record, find similar records.
     */
    public abstract java.util.List<Record> findSimilar(Record q) throws DeepEyeException;

    /**
     * given the shell of a annot, find similar records.
     */
    public abstract java.util.List<Annotation> findSimilar(Annotation q);
}
