/**
 * PacketForwarderRequestAttributes
 * Copyright (c) 2014 Frank Duerr
 *
 * PacketForwarderRequestAttributes is part of SDN-MQ. This program and the 
 * accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.sdnmq.jms.json;

/**
 * This class defines all possible attributes (keys) of a 
 * packet-out request object in JSON representation.
 * 
 * @author Frank Duerr
 */
public class PacketForwarderRequestAttributes {
    /**
     * Keys if the JSON object.
     */
    public enum Keys {
        NODE("node"),
        EGRESS_PORT("egressPort"),
        PACKET("packet");
        
        private String json;
        
        Keys(String json) {
            this.json = json;
        }
        
        public String toJSON() {
            return json;
        }
    }
}
