/**
 * MatchAttributes
 * Copyright (c) 2014 Frank Duerr
 *
 * MatchAttributes is part of SDN-MQ. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.sdnmq.jms.json;

/**
 * This class defines all possible attributes (keys) of a match object in JSON representation.
 * 
 * @author Frank Duerr
 */
public class MatchAttributes {
    public enum Keys {
        DL_SRC("dlSrc"),
        DL_DST("dlDst"),
        DL_VLAN("dlVlan"),
        DL_VLAN_PRIORITY("dlVlanPriority"),
        ETHERTYPE("etherType"),
        NW_SRC("nwSrc"),
        NW_SRC_MASK("nwSrcMask"),
        NW_DST("nwDst"),
        NW_DST_MASK("nwDstMask"),
        NW_TOS("nwTos"),
        PROTOCOL("protocol"),
        TP_SRC("tpSrc"),
        TP_DST("tpDst"),
        INGRESS_PORT("ingressPort");
        
        private String json;
        
        Keys(String json) {
            this.json = json;
        }
        
        public String toJSON() {
            return json;
        }
    }
}
