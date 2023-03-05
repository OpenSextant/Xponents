package org.opensextant.annotations;
/*
 * IIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIII
 *
 * Xponents sub-project "DeepEye", NLP methodology
 *
 * IIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIII
 * Copyright 2013-2021 MITRE Corporation
 */

/**
 * Exception used when there is a user or system error related to data
 * serialization or any sort of
 * Java object - to JSONification error.
 *
 * @author ubaldino
 */
public class DeepEyeException extends Exception {

    private static final long serialVersionUID = 1234567890L;

    public DeepEyeException(Exception ex) {
        super(ex);
    }

    public DeepEyeException(String msg) {
        super(msg);
    }

    public DeepEyeException(String msg, Exception ex) {
        super(msg, ex);
    }
}
