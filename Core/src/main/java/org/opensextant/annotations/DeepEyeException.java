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

/**
 * Exception used when there is a user or system error related to data serialization or any sort of
 * Java object - to JSONification error.
 * 
 * @author ubaldino
 */
public class DeepEyeException extends Exception {

    private final static long serialVersionUID = 1234567890l;

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
