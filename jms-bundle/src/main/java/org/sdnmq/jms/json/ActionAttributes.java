/**
 * ActionAttributes
 * Copyright (c) 2014 Frank Duerr
 *
 * ActionAttributes is part of SDN-MQ. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0, which accompanies this distribution
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.sdnmq.jms.json;

/**
 * This class defines all possible attributes (keys and for some selected keys possible values) of 
 * an Action object in JSON represenation.
 * 
 * @author Frank Duerr
 */
public class ActionAttributes {   
    
    /**
     * Keys of the JSON object (which keys are actually present, depends on the individual type of action).
     */
    public enum Keys {
        ACTION("action"),
        PORT("port"),
        DL_ADDRESS("dlAddress"),
        NW_ADDRESS("nwAddress"),
        TP_ADDRESS("tpAddress"),
        PCP("pcp"),
        DEI("dei"),
        VLAN_ID("vlanId");
        
        private String json;
        
        Keys(String json) {
            this.json = json;
        }
        
        public String toJSON() {
            return json;
        }
    }
    
    /**
     * Possible values of the key ACTION_TYPE.
     */
    public enum ActionTypeValues {
        LOOPBACK("loopback"),
        OUTPUT("output"),
        FLOOD("flood"),
        DROP("drop"),
        CONTROLLER("controller"),
        SET_DL_SRC("setDlSrc"),
        SET_DL_DST("setDlDst"),
        SET_NW_SRC("setNwSrc"),
        SET_NW_DST("setNwDst"),
        SET_TP_SRC("setTpSrc"),
        SET_TP_DST("setTpDst"),
        PUSH_VLAN("pushVLAN"),
        POP_VLAN("popVLAN");
        
        private String json;
        
        ActionTypeValues(String json) {
            this.json = json;
        }
        
        public String toJSON() {
            return json;
        }
    }
}
