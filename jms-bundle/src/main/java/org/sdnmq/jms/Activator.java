/**
 * Activator
 * Copyright (c) 2014 Frank Duerr
 *
 * Activator is part of SDN-MQ. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.sdnmq.jms;

import java.util.Dictionary;
import java.util.Hashtable;

import org.apache.felix.dm.Component;
import org.opendaylight.controller.sal.core.ComponentActivatorAbstractBase;
import org.opendaylight.controller.sal.flowprogrammer.IFlowProgrammerService;
import org.opendaylight.controller.sal.packet.IDataPacketService;
import org.opendaylight.controller.sal.packet.IListenDataPacket;
import org.opendaylight.controller.switchmanager.ISwitchManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * OSGi component activator registering our services and declaring dependencies to other OpenDaylight services.
 * 
 * @author Frank Duerr
 */
public class Activator extends ComponentActivatorAbstractBase {
    private static final Logger log = LoggerFactory.getLogger(PacketHandler.class);

    public Object[] getImplementations() {
        log.trace("Getting Implementations");
        
        Object[] res = { PacketHandler.class, PacketForwarder.class, FlowProgrammer.class };
        return res;
    }

    public void configureInstance(Component c, Object imp, String containerName) {
        if (imp.equals(PacketHandler.class)) {
            log.trace("Configuring packet handler");
         
            // Export IListenDataPacket interface to receive packet-in events.
            Dictionary<String, Object> props = new Hashtable<String, Object>();
            props.put("salListenerName", "sdnmq");
            c.setInterface(new String[] {IListenDataPacket.class.getName()}, props);

            // Need the DataPacketService for decoding data packets
            c.add(createContainerServiceDependency(containerName).setService(
                    IDataPacketService.class).setCallbacks(
                            "setDataPacketService", "unsetDataPacketService").setRequired(true));

        } else if (imp.equals(PacketForwarder.class)) {
            log.trace("Configuring packet forwarder");
            
            // Need the DataPacketService for encoding and sending packets
            c.add(createContainerServiceDependency(containerName).setService(
                    IDataPacketService.class).setCallbacks(
                            "setDataPacketService", "unsetDataPacketService").setRequired(true));
            
            // Need SwitchManager service for finding nodes and node connectors
            c.add(createContainerServiceDependency(containerName).setService(
                    ISwitchManager.class).setCallbacks(
                            "setSwitchManagerService", "unsetSwitchManagerService").setRequired(true));
        } else if (imp.equals(FlowProgrammer.class)) {
            log.trace("Configuring flow programmer");
            
            // Need SwitchManager service for finding nodes and node connectors
            c.add(createContainerServiceDependency(containerName).setService(
                    ISwitchManager.class).setCallbacks(
                            "setSwitchManagerService", "unsetSwitchManagerService").setRequired(true));
            
            // Need FlowProgrammerService for programming flows
            c.add(createContainerServiceDependency(containerName).setService(
                    IFlowProgrammerService.class).setCallbacks(
                            "setFlowProgrammerService", "unsetFlowProgrammerService").setRequired(true));
        }
    }
}
