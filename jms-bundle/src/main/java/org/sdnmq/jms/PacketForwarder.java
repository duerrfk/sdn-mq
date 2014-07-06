/**
 * PacketForwarder
 * Copyright (c) 2014 Frank Duerr
 *
 * PacketForwarder is part of SDN-MQ. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.sdnmq.jms;

import java.util.Properties;

import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageListener;
import javax.jms.Queue;
import javax.jms.QueueConnection;
import javax.jms.QueueConnectionFactory;
import javax.jms.QueueReceiver;
import javax.jms.QueueSession;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.xml.bind.DatatypeConverter;

import org.json.JSONException;
import org.json.JSONObject;
import org.opendaylight.controller.sal.core.ConstructionException;
import org.opendaylight.controller.sal.core.Node;
import org.opendaylight.controller.sal.core.NodeConnector;
import org.opendaylight.controller.sal.packet.IDataPacketService;
import org.opendaylight.controller.sal.packet.RawPacket;
import org.opendaylight.controller.switchmanager.ISwitchManager;
import org.sdnmq.jms.json.NodeAttributes;
import org.sdnmq.jms.json.PacketForwarderRequestAttributes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Packet Forwarder service retrieving packet forwarding requests from JMS and forwarding request to OpenDaylight.
 * 
 * @author Frank Duerr
 */
public class PacketForwarder implements MessageListener {
    private static final Logger log = LoggerFactory.getLogger(PacketForwarder.class);
 
    /**
     *  This property can be defined in the OpenDaylight configuration to change the 
     *  packet forwarder request queue name (JNDI name of queue object):
     *  $OPENDAYLIGHTHOME/configuration/config.ini
     */
    private static final String PACKETOUT_QUEUE_PROPERTY = "sdnmq.queuename.packetout";
    private static final String DEFAULT_PACKETOUT_QUEUE_NAME = "org.sdnmq.packetout";
    
    private QueueConnection connection = null;
    private QueueSession session = null;
    private QueueReceiver receiver = null;
    private Queue packetOutQueue = null;
    
    private IDataPacketService dataPacketService = null;
    private ISwitchManager switchManager = null;
    
    /**
     * Bind to DataPacketService.
     */
    void setDataPacketService(IDataPacketService s) {
        log.trace("Bind to DataPacketService.");

        dataPacketService = s;
    }

    /**
     * Unbind from DataPacketService.
     */
    void unsetDataPacketService(IDataPacketService s) {
        log.trace("Unbind from DataPacketService.");

        if (dataPacketService == s) {
            dataPacketService = null;
        }
    }
    
    /**
     * Bind to SwitchManagerService
     */
    void setSwitchManagerService(ISwitchManager s) {
       log.trace("Bind to SwitchManagerService.");
     
       switchManager = s;
    }
     
    /**
     * Unbind from SwitchManagerService
     */
    void unsetSwitchManagerService(ISwitchManager s) {
        log.trace("Unbind from SwitchManagerService.");
     
        if (switchManager == s) {
            switchManager = null;
        }
    }
    
    /**
     * Function called by the dependency manager if all the required
     * dependencies are satisfied.
     */
    public void init() {
        if (initMQ()) {
            log.trace("MQ setup successful");
            startMsgListener();
        } else {
            log.error("Could not init MQ");
        }
    }
    
    /**
     * Setup MQ
     */
    private boolean initMQ() {
        log.trace("Setting up MQ system");
        
        Properties jndiProps = JNDIHelper.getJNDIProperties();
        
        Context ctx = null;
        try {
            ctx = new InitialContext(jndiProps);
        } catch (NamingException e) {
            log.error(e.getMessage());
            releaseMQ();
            return false;
        }
        
        QueueConnectionFactory queueFactory = null;
        try {
            queueFactory = (QueueConnectionFactory) ctx.lookup("QueueConnectionFactory");
        } catch (NamingException e) {
            log.error(e.getMessage());
            releaseMQ();
            return false;
        }
        
        try {
            connection = queueFactory.createQueueConnection();
        } catch (JMSException e) {
            log.error(e.getMessage());
            releaseMQ();
            return false;
        }
        
        try {
            session = connection.createQueueSession(false, Session.AUTO_ACKNOWLEDGE);
        } catch (JMSException e) {
            log.error(e.getMessage());
            releaseMQ();
            return false;
        }
        
        String queueName = System.getProperty(PACKETOUT_QUEUE_PROPERTY, DEFAULT_PACKETOUT_QUEUE_NAME);
        log.info("Using the following queue for packet forwarding requests: " + queueName);
        try {
            packetOutQueue = (Queue) ctx.lookup(queueName);
        } catch (NamingException e) {
            log.error(e.getMessage());
            releaseMQ();
            return false;
        }
        
        try {
            receiver = session.createReceiver(packetOutQueue);
        } catch (JMSException e) {
            log.error(e.getMessage());
            releaseMQ();
            return false;
        }
        
        return true;
    }
    
    /**
     * Release MQ-related objects.
     */
    private void releaseMQ() {
        if (connection != null) {
            try {
                connection.stop();
            } catch (JMSException e) {}
        }
        if (receiver != null) {
            try {
                receiver.close();
            } catch (JMSException e) {}
        }
        
        if (session != null) {
            try {
                session.close();
            } catch (JMSException e) {}
        }
        
        if (connection != null) {
           try {
            connection.close();
           } catch (JMSException e) {}
        }
    }
    
    /**
     * Starts the receiver thread.
     */
    private void startMsgListener() {
        try {
            receiver.setMessageListener(this);
        } catch (JMSException e) {
            log.error(e.getMessage());
            return;
        }
        
        try {
            connection.start();
        } catch (JMSException e) {
            log.error(e.getMessage());
            return;
        }
    }

    @Override
    public void onMessage(Message msg) {
        log.trace("Received packet forwarding request.");
        
        // TODO: Check, how we can send an error message to the requester if something goes wrong.
        
        // Parse JSON
        
        if (!(msg instanceof TextMessage)) {
            log.error("Received invalid message type (not a text message).");
            return;
        }
        
        JSONObject json = null;
        try {
            json = new JSONObject(((TextMessage) msg).getText());
        } catch (JSONException e) {
            log.error("Could not parse JSON message: " + e.getMessage());
            return;
        } catch (JMSException e) {
            log.error(e.getMessage());
            return;
        }
        assert(json != null);
        log.trace(json.toString());
        
        // Get node information
        
        String nodeId = null;
        String nodeType = null;
        try {
            JSONObject nodeJson = json.getJSONObject(PacketForwarderRequestAttributes.Keys.NODE.toJSON());
            nodeId = nodeJson.getString(NodeAttributes.Keys.ID.toJSON());
            nodeType = nodeJson.getString(NodeAttributes.Keys.TYPE.toJSON());
        } catch (JSONException e) {
            log.error("Node attributes not specified: " + e.getMessage());
            return;
        }
        
        // Outgoing port
        
        String outPort = null;
        try {
            outPort = json.getString(PacketForwarderRequestAttributes.Keys.EGRESS_PORT.toJSON());
        } catch (JSONException e) {
            log.error("Outport not specified: " + e.getMessage());
            return;
        }
        
        // The raw packet to be sent.
        
        byte[] packetData = null;
        try {
            packetData = DatatypeConverter.parseBase64Binary(json.getString(PacketForwarderRequestAttributes.Keys.PACKET.toJSON()));
        } catch (JSONException e) {
            log.error("Packet data not specified: " + e.getMessage());
            return;
        }
        
        assert(nodeId != null);
        assert(nodeType != null);
        assert(packetData != null);
        assert(outPort != null);
        
        // Try to find node and node connector
        
        Node node = Node.fromString(nodeType, nodeId);
        if (node == null) {
            log.error("Invalid node id: " + nodeId);
            return;
        }
        
        if (!switchManager.getNodes().contains(node)) {
            log.error("Node '" + node.getID() + "' not found");
            return;
        }
        
        NodeConnector connector = NodeConnector.fromStringNoNode(outPort, node);
        if (!switchManager.doesNodeConnectorExist(connector)) {
            log.error("Port '" + outPort + "' does not exist on node '" + node.getID() + "'");
            return;
        }
        
        // Construct packet 
        
        RawPacket pkt;
        try {
            pkt = new RawPacket(packetData);
        } catch (ConstructionException e) {
            log.error("Invalid packet data: " + e.getMessage());
            return;
        }
        
        // Send packet via Data Packet Service
        
        dataPacketService.transmitDataPacket(pkt);
    }
}
