/**
 * Packet Handler
 * Copyright (c) 2014 Frank Duerr
 *
 * PacketHandler is part of SDN-MQ. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.sdnmq.jms;

import java.util.Properties;

import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.jms.Topic;
import javax.jms.TopicConnection;
import javax.jms.TopicConnectionFactory;
import javax.jms.TopicPublisher;
import javax.jms.TopicSession;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.xml.bind.DatatypeConverter;

import org.json.JSONObject;
import org.opendaylight.controller.sal.core.Node;
import org.opendaylight.controller.sal.core.NodeConnector;
import org.opendaylight.controller.sal.packet.Ethernet;
import org.opendaylight.controller.sal.packet.IDataPacketService;
import org.opendaylight.controller.sal.packet.IEEE8021Q;
import org.opendaylight.controller.sal.packet.IListenDataPacket;
import org.opendaylight.controller.sal.packet.IPv4;
import org.opendaylight.controller.sal.packet.Packet;
import org.opendaylight.controller.sal.packet.PacketResult;
import org.opendaylight.controller.sal.packet.RawPacket;
import org.opendaylight.controller.sal.packet.TCP;
import org.opendaylight.controller.sal.packet.UDP;
import org.sdnmq.jms.json.NodeAttributes;
import org.sdnmq.jms.json.PacketInAttributes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Packet-in handler sending for each OpenFlow packet-in event a JMS notification to the specified packet-in topic.
 * 
 * @author Frank Duerr
 */
public class PacketHandler implements IListenDataPacket {
    private static final Logger log = LoggerFactory.getLogger(PacketHandler.class);
    
    /**
     *  This property can be defined in the OpenDaylight configuration to change the 
     *  packet-in topic name (JNDI name of topic object):
     *  $OPENDAYLIGHTHOME/configuration/config.ini
     */
    private static final String PACKETIN_TOPIC_PROPERTY = "sdnmq.topicname.packetin";
    private static final String DEFAULT_PACKETIN_TOPIC_NAME = "org.sdnmq.packetin";
    
    private TopicConnection connection = null;
    private TopicSession session = null;
    private TopicPublisher publisher = null;
    private Topic packetinTopic = null;
    
    private IDataPacketService dataPacketService = null;
       
    /**
     * Called by the dependency manager if all required 
     * dependencies are satisfied.
     */
    public void init() {
        initMQ();
    }
    
    /**
     * Initialization of JMS.
     */
    private void initMQ() {
        log.trace("Setting up JMS ...");
        
        Properties jndiProps = JNDIHelper.getJNDIProperties();
        
        Context ctx = null;
        try {
            ctx = new InitialContext(jndiProps);
        } catch (NamingException e) {
            log.error(e.getMessage());
            releaseMQ();
            return;
        }
        
        TopicConnectionFactory topicFactory = null;
        try {
            topicFactory = (TopicConnectionFactory) ctx.lookup("TopicConnectionFactory");
        } catch (NamingException e) {
            log.error(e.getMessage());
            releaseMQ();
            return;
        }
        
        try {
            connection = topicFactory.createTopicConnection();
        } catch (JMSException e) {
            log.error("Could not create JMS connection: " + e.getMessage());
            releaseMQ();
            return;
        }
        
        try {
            session = connection.createTopicSession(false, Session.AUTO_ACKNOWLEDGE);
        } catch (JMSException e) {
            log.error("Could not create JMS session: " + e.getMessage());
            releaseMQ();
            return;
        }
        
        // Get the JNDI object name of the packet-in topic object from the OpenDaylight configuration.
        String topicName = System.getProperty(PACKETIN_TOPIC_PROPERTY, DEFAULT_PACKETIN_TOPIC_NAME);
        log.info("Using the following topic for packet-in events: " + topicName);
        try {
            packetinTopic = (Topic) ctx.lookup(topicName);
        } catch (NamingException e) {
            log.error("Could not resolve topic object: " + e.getMessage());
            releaseMQ();
            return;
        }
        
        try {
            publisher = session.createPublisher(packetinTopic);
        } catch (JMSException e) {
            log.error(e.getMessage());
            releaseMQ();
            return;
        }
        
        log.trace("JMS setup finished successfully");
    }
    
    /**
     * Releases JMS-related objects.
     */
    private void releaseMQ() {
        if (publisher != null) {
            try {
                publisher.close();
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
     * Callback invoked by OpenDaylight when DataPacketService is bound.
     */
    void setDataPacketService(IDataPacketService s) {
        log.trace("Set DataPacketService.");

        dataPacketService = s;
    }

    /**
     * Callback called by OpenDaylight when DataPacketService is unbound.
     */
    void unsetDataPacketService(IDataPacketService s) {
        log.trace("Removed DataPacketService.");

        if (dataPacketService == s) {
            dataPacketService = null;
        }
    }
    
    /**
     * Converts an IPv4 packet to JSON representation.
     * 
     * @param ipv4 the IPv4 packet
     * @param json the JSON object to which the information will be added
     */
    private void ipv4ToJSON(IPv4 ipv4, JSONObject json) {
        json.put(PacketInAttributes.Keys.NW_SRC.toJSON(), Netutil.ipv4ToStr(ipv4.getSourceAddress()));
        json.put(PacketInAttributes.Keys.NW_DST.toJSON(), Netutil.ipv4ToStr(ipv4.getDestinationAddress()));
        json.put(PacketInAttributes.Keys.PROTOCOL.toJSON(), (short) ipv4.getProtocol());
    }
    
    /**
     * Adds message properties according to IPv4 header fields.
     * 
     * @param ipv4 the IPv4 packet
     * @param msg the message whose properties are set
     */
    private void ipv4ToProperties(IPv4 ipv4, Message msg) {
        try {
            msg.setStringProperty(MessageFilterAttributes.Keys.NW_SRC.toFilterName(), Netutil.ipv4ToStr(ipv4.getSourceAddress()));
            msg.setStringProperty(MessageFilterAttributes.Keys.NW_SRC_BINARY.toFilterName(), Netutil.ipv4ToBinaryStr(ipv4.getSourceAddress()));
        } catch (JMSException e) {
            log.error(e.getMessage());
        }
        
        try {
            msg.setStringProperty(MessageFilterAttributes.Keys.NW_DST.toFilterName(), Netutil.ipv4ToStr(ipv4.getDestinationAddress()));
            msg.setStringProperty(MessageFilterAttributes.Keys.NW_DST_BINARY.toFilterName(), Netutil.ipv4ToBinaryStr(ipv4.getDestinationAddress()));
        } catch (JMSException e) {
            log.error(e.getMessage());
        }
        
        try {
            msg.setShortProperty(MessageFilterAttributes.Keys.NW_PROTOCOL.toFilterName(), (short) ipv4.getProtocol());
        } catch (JMSException e) {
            log.error(e.getMessage());
        }
    }
    
    /**
     * Converts an Ethernet frame to JSON representation.
     * 
     * @param ethernet the Ethernet frame
     * @param json the JSON object to which the information will be added
     */
    private void ethernetToJSON(Ethernet ethernet, JSONObject json) {
        json.put(PacketInAttributes.Keys.DL_SRC.toJSON(), Netutil.macToStr(ethernet.getSourceMACAddress()));
        json.put(PacketInAttributes.Keys.DL_DST.toJSON(), Netutil.macToStr(ethernet.getDestinationMACAddress()));
        json.put(PacketInAttributes.Keys.ETHERTYPE.toJSON(), ethernet.getEtherType());
    }
    
    /**
     * Adds message properties according to Ethernet header fields.
     * 
     * @param ethernet the Ethernet frame
     * @param msg the message object to which the information will be added
     */
    private void ethernetToProperties(Ethernet ethernet, Message msg) {
        try {
            msg.setStringProperty(MessageFilterAttributes.Keys.DL_SRC.toFilterName(), Netutil.macToStr(ethernet.getSourceMACAddress()));
        } catch (JMSException e) {
            log.error(e.getMessage());
        }
        
        try {
            msg.setStringProperty(MessageFilterAttributes.Keys.DL_DST.toFilterName(), Netutil.macToStr(ethernet.getDestinationMACAddress()));
        } catch (JMSException e) {
            log.error(e.getMessage());
        }
        
        try {
            msg.setShortProperty(MessageFilterAttributes.Keys.DL_TYPE.toFilterName(), ethernet.getEtherType());
        } catch (JMSException e) {
            log.error(e.getMessage());
        }
    }
    
    /**
     * Converts an IEEE802.1q frame to JSON representation.
     * 
     * @param ethernet the IEEE802.q frame
     * @param json the JSON object to which the information will be added
     */
    private void ieee8021qToJSON(IEEE8021Q ieee8021q, JSONObject json) {
        json.put(PacketInAttributes.Keys.DL_VLAN.toJSON(), ieee8021q.getVid());
        json.put(PacketInAttributes.Keys.DL_VLAN_PRIORITY.toJSON(), ieee8021q.getPcp());
    }
    
    /**
     * Adds properties to a message according to IEEE802.1q frame information.
     * 
     * @param ieee8021q the IEEE802.q frame
     * @param json the JSON object to which the information will be added
     */
    private void ieee8021qToProperties(IEEE8021Q ieee8021q, Message msg) {
        try {
            msg.setShortProperty(MessageFilterAttributes.Keys.DL_VLAN.toFilterName(), (short) ieee8021q.getVid());
        } catch (JMSException e) {
            log.error(e.getMessage());
        }
        
        try {
            msg.setByteProperty(MessageFilterAttributes.Keys.DL_VLAN_PR.toFilterName(), ieee8021q.getPcp());
        } catch (JMSException e) {
            log.error(e.getMessage());
        }
    }
    
    /**
     * Converts a TCP datagram to JSON representation.
     * 
     * @param tcp the TCP datagram
     * @param json the JSON object to which the information will be added
     */
    private void tcpToJSON(TCP tcp, JSONObject json) {
        json.put(PacketInAttributes.Keys.TP_SRC.toJSON(), tcp.getSourcePort());
        json.put(PacketInAttributes.Keys.TP_DST.toJSON(), tcp.getDestinationPort());
    }
    
    /**
     * Adds properties to a message according to TCP datagram header fields.
     * 
     * @param tcp the TCP datagram
     * @param msg the message to which the information will be added
     */
    private void tcpToProperties(TCP tcp, Message msg) {
        try {
            msg.setShortProperty(MessageFilterAttributes.Keys.TP_SRC.toFilterName(), tcp.getSourcePort());
        } catch (JMSException e) {
            log.error(e.getMessage());
        }
        
        try {
            msg.setShortProperty(MessageFilterAttributes.Keys.TP_DST.toFilterName(), tcp.getDestinationPort());
        } catch (JMSException e) {
            log.error(e.getMessage());
        }
    }
    
    /**
     * Converts a UDP datagram to JSON representation.
     * 
     * @param udp the UDP datagram
     * @param json the JSON object to which the information will be added
     */
    private void udpToJSON(UDP udp, JSONObject json) {
        json.put(PacketInAttributes.Keys.TP_SRC.toJSON(), udp.getSourcePort());
        json.put(PacketInAttributes.Keys.TP_DST.toJSON(), udp.getDestinationPort());
    }
    
    /**
     * Sets message properties according to UDP datagram header fields.
     * 
     * @param udp the UDP datagram
     * @param msg the message to which the information will be added
     */
    private void udpToProperties(UDP udp, Message msg) {
        try {
            msg.setShortProperty(MessageFilterAttributes.Keys.TP_SRC.toFilterName(), udp.getSourcePort());
        } catch (JMSException e) {
            log.error(e.getMessage());
        }
        
        try {
            msg.setShortProperty(MessageFilterAttributes.Keys.TP_DST.toFilterName(), udp.getDestinationPort());
        } catch (JMSException e) {
            log.error(e.getMessage());
        }
    }
    
    /**
     * Converts a packet to JSON representation.
     * 
     * @param pkt the packet to be converted
     * @return JSON representation.
     */
    private JSONObject pktToJSON(RawPacket rawPkt) {
        log.trace("Received packet-in event.");
        
        JSONObject json = new JSONObject();
        
        // Add incoming node
        
        // The connector, the packet came from ("port")
        NodeConnector ingressConnector = rawPkt.getIncomingNodeConnector();
        // The node that received the packet ("switch")
        Node node = ingressConnector.getNode();

        JSONObject nodeJson = new JSONObject();
        nodeJson.put(NodeAttributes.Keys.ID.toJSON(), node.getNodeIDString());
        nodeJson.put(NodeAttributes.Keys.TYPE.toJSON(), node.getType());
        json.put(PacketInAttributes.Keys.NODE.toJSON(), nodeJson);
        
        // Add inport
        
        json.put(PacketInAttributes.Keys.INGRESS_PORT.toJSON(), ingressConnector.getNodeConnectorIDString());
        
        // Add raw packet data
        
        // Use DataPacketService to decode the packet.
        Packet pkt = dataPacketService.decodeDataPacket(rawPkt);

        while (pkt != null) {
            if (pkt instanceof Ethernet) {
                Ethernet ethernet = (Ethernet) pkt;
                ethernetToJSON(ethernet, json);
            } else if(pkt instanceof IEEE8021Q) {
                IEEE8021Q ieee802q = (IEEE8021Q) pkt;
                ieee8021qToJSON(ieee802q, json);
            } else if (pkt instanceof IPv4) {
                IPv4 ipv4 = (IPv4) pkt;
                ipv4ToJSON(ipv4, json);
            } else if (pkt instanceof TCP) {
                TCP tcp = (TCP) pkt;
                tcpToJSON(tcp, json);
            } else if (pkt instanceof UDP) {
                UDP udp = (UDP) pkt;
                udpToJSON(udp, json);
            }
            
            pkt = pkt.getPayload();
        }
        
        json.put(PacketInAttributes.Keys.PACKET.toJSON(), DatatypeConverter.printBase64Binary(rawPkt.getPacketData()));
        
        return json;
    }
    
    /**
     * Sets the properties of the message according to the message header fields for content-based filtering.
     * 
     * @param msg the message whose properties are set
     * @param pkt the packet from where the message properties are derived
     */
    private void setMsgProperties(Message msg, RawPacket rawPkt) {
        // The connector, the packet came from ("port")
        NodeConnector ingressConnector = rawPkt.getIncomingNodeConnector();
        // The node that received the packet ("switch")
        Node node = ingressConnector.getNode();

        try {
            msg.setStringProperty(MessageFilterAttributes.Keys.NODE_ID.toFilterName(), node.getNodeIDString());
            msg.setStringProperty(MessageFilterAttributes.Keys.NODE_TYPE.toFilterName(), node.getType());
        } catch (JMSException e) {
            log.error(e.getMessage());
        }
        
        try {
            msg.setStringProperty(MessageFilterAttributes.Keys.INPORT.toFilterName(), ingressConnector.getNodeConnectorIDString());
        } catch (JMSException e) {
            log.error(e.getMessage());
        }
        
        // Use DataPacketService to decode the packet.
        Packet pkt = dataPacketService.decodeDataPacket(rawPkt);

        while (pkt != null) {
            if (pkt instanceof Ethernet) {
                Ethernet ethernet = (Ethernet) pkt;
                ethernetToProperties(ethernet, msg);
            } else if (pkt instanceof IEEE8021Q) {
                IEEE8021Q ieee802q = (IEEE8021Q) pkt;
                ieee8021qToProperties(ieee802q, msg);
            } else if (pkt instanceof IPv4) {
                IPv4 ipv4 = (IPv4) pkt;
                ipv4ToProperties(ipv4, msg);
            } else if (pkt instanceof TCP) {
                TCP tcp = (TCP) pkt;
                tcpToProperties(tcp, msg);
            } else if (pkt instanceof UDP) {
                UDP udp = (UDP) pkt;
                udpToProperties(udp, msg);
            }
            
            pkt = pkt.getPayload();
        }
    }
    
    @Override
    public PacketResult receiveDataPacket(RawPacket inPkt) {
        log.trace("Received data packet.");

        // Convert packet to JSON representation.
        String jsonStr = pktToJSON(inPkt).toString();
                
        // Send notification to JMS topic.
        TextMessage message;
        try {
            if (session != null && publisher != null) {
                message = session.createTextMessage(jsonStr);
                setMsgProperties(message, inPkt);
                log.trace("Publishing the following packet-in event: " + jsonStr);
                publisher.send(message);
            } else {
                log.error("Cannot publish packet-in event. JMS not setup.");
            }
        } catch (JMSException e) {
            log.error("Error while publishing packet-in event: " + e.getMessage());
            return PacketResult.IGNORED;
        }
        
        // Also let other message handlers process this packet. 
        // If you don't want these services, remove them from
        // the OpenDaylight configuration.
        return PacketResult.KEEP_PROCESSING;
    }

}
