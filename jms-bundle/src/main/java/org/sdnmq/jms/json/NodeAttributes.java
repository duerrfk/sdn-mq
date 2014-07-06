/**
 * NodeAttributes
 * Copyright (c) 2014 Frank Duerr
 *
 * NodeAttributes is part of SDN-MQ. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0, which accompanies this distribution
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.sdnmq.jms.json;

/**
 * This class defines all possible attributes (keys) of a 
 * Node Attribute object in JSON representation.
 * 
 * @author Frank Duerr 
 */
public class NodeAttributes {
    /**
     * Possible keys of the JSON object.
     */
    public enum Keys {
        ID("id"),
        TYPE("type");
        
        private String json;
        
        Keys(String json) {
            this.json = json;
        }
        
        public String toJSON() {
            return json;
        }
    }
    
    /**
     * Predefined node types.
     */
    public enum TypeValues {
        OF("OF");
        
        private String json;
        
        TypeValues(String json) {
            this.json = json;
        }
        
        public String toJSON() {
            return json;
        }
    }
}
