/**
 * MessageFilterAttributes
 * Copyright (c) 2014 Frank Duerr
 *
 * MessageFilterAttributes is part of SDN-MQ. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.sdnmq.jms;

/**
 * This class defines all possible message attributes for filtering 
 * a packet-in notification.
 * 
 * @author Frank Duerr
 *
 */
public class MessageFilterAttributes {
    public enum Keys {
        DL_SRC("dlSrc"),
        DL_DST("dlDst"),
        DL_VLAN("dlVlan"),
        DL_VLAN_PR("dlVlanPriority"),
        DL_TYPE("etherType"),
        NW_SRC("nwSrc"),
        NW_SRC_BINARY("nwSrcBin"),
        NW_SRC_MASK("nwSrcMask"),
        NW_DST("nwDst"),
        NW_DST_BINARY("nwDstBin"),
        NW_DST_MASK("nwDstMask"),
        NW_TOS("nwTos"),
        NW_PROTOCOL("protocol"),
        TP_SRC("tpSrc"),
        TP_DST("tpDst"),
        NODE_ID("node"),
        NODE_TYPE("nodeType"),
        INPORT("ingressPort");
        
        private String filterName;
        
        Keys(String filterName) {
            this.filterName = filterName;
        }
        
        public String toFilterName() {
            return filterName;
        }
    }
}
