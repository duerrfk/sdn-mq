/**
 * FlowProgrammerRequestAttributes
 * Copyright (c) 2014 Frank Duerr
 *
 * FlowProgrammerRequestAttributes is part of SDN-MQ. This program and the accompanying materials are 
 * made available under the terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.sdnmq.jms.json;

/**
 * This class defines all possible attributes (keys and for some selected keys possible values) of a 
 * Flow Programmer request object in JSON.
 * 
 * @author Frank Duerr
 */
public class FlowProgrammerRequestAttributes {
    /**
     * Possible keys of the JSON object.
     */
    public enum Keys {
        COMMAND("command"),
        FLOW("flow"),
        NODE("node"),
        FLOW_NAME("flowName");
        
        private String json;
        
        Keys(String json) {
            this.json = json;
        }
        
        public String toJSON() {
            return json;
        }
    }
    
    /**
     * Possible values of the key COMMAND.
     */
    public enum CommandValues {
        ADD("add"),
        MODIFY("modify"),
        DELETE("delete");
        
        private String json;
        
        CommandValues(String json) {
            this.json = json;
        }
        
        public String toJSON() {
            return json;
        }
    }
}
