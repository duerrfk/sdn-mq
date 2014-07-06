/**
 * SimplePacketSender
 * Copyright (c) 2014 Frank Duerr
 *
 * SimplePacketSender is part of SDN-MQ. This program and the accompanying materials 
 * are made available under the terms of the Eclipse Public License v1.0 which 
 * accompanies this distribution, and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.sdnmq.jms_demoapps;

import java.net.InetAddress;
import java.net.UnknownHostException;

import javax.jms.JMSException;
import javax.jms.Queue;
import javax.jms.QueueConnection;
import javax.jms.QueueConnectionFactory;
import javax.jms.QueueSender;
import javax.jms.QueueSession;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NameNotFoundException;
import javax.naming.NamingException;
import javax.xml.bind.DatatypeConverter;

import org.json.JSONArray;
import org.json.JSONObject;
import org.opendaylight.controller.sal.packet.Ethernet;
import org.opendaylight.controller.sal.packet.IPv4;
import org.opendaylight.controller.sal.packet.PacketException;
import org.opendaylight.controller.sal.packet.RawPacket;
import org.opendaylight.controller.sal.packet.UDP;
import org.sdnmq.jms.json.ActionAttributes;
import org.sdnmq.jms.json.FlowAttributes;
import org.sdnmq.jms.json.FlowProgrammerRequestAttributes;
import org.sdnmq.jms.json.MatchAttributes;
import org.sdnmq.jms.json.NodeAttributes;
import org.sdnmq.jms.json.PacketForwarderRequestAttributes;

/**
 * Demo applications for SDN-MQ showing how to use the packer forwarder.
 * 
 * For a general introduction to JMS, you might want to read this tutorial:
 * http://docs.oracle.com/javaee/1.3/jms/tutorial/
 * 
 * @author Frank Duerr
 */
public class SimplePacketSender {
    static final String PACKETFORWARDER_QUEUE_NAME = "org.sdnmq.packetout";
    
    private static Context ctx = null;
    private static QueueConnectionFactory queueFactory = null;
    private static QueueConnection connection = null;
    private static QueueSession session = null;
    private static Queue packetForwarderQueue = null;
    private static QueueSender sender = null;
    
    private static void die(int status) {
        if (sender != null) {
            try {
                sender.close();
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
           
        System.exit(status);
    }
    
    public static void main(String[] args) {
        // Standard JMS setup.
        try {
            // Uses settings from file jndi.properties if file is in CLASSPATH.
            ctx = new InitialContext();
        } catch (NamingException e) {
            System.err.println(e.getMessage());
            die(-1);
        }  
        
        try {
            queueFactory = (QueueConnectionFactory) ctx.lookup("QueueConnectionFactory");
        } catch (NamingException e) {
            System.err.println(e.getMessage());
            die(-1);
        }
        
        try {
            connection = queueFactory.createQueueConnection();
        } catch (JMSException e) {
            System.err.println(e.getMessage());
            die(-1);
        }
        
        try {
            session = connection.createQueueSession(false, Session.AUTO_ACKNOWLEDGE);
        } catch (JMSException e) {
            System.err.println(e.getMessage());
            die(-1);
        }
        
        try {
            packetForwarderQueue = (Queue) ctx.lookup(PACKETFORWARDER_QUEUE_NAME);
        } catch(NameNotFoundException e) {
            System.err.println(e.getMessage());
            die(-1);
        } catch (NamingException e) {
            System.err.println(e.getMessage());
            die(-1);
        }
        
        try {
            sender = session.createSender(packetForwarderQueue);
        } catch (JMSException e) {
            System.err.println(e.getMessage());
            die(-1);
        }
        
        try {
            connection.start();
        } catch (JMSException e) {
            System.err.println(e.getMessage());
            die(-1);
        }
        
        // Create packet using OpenDaylight packet classes (or any other packet parser/constructor you like most)
        // In most use cases, you will not create the complete packet yourself from scratch
        // but rather use a packet received from a packet-in event as basis. Let's assume that
        // the received packet-in event looks like this (in JSON representation as delivered by
        // the packet-in handler):
        //
        // {"node":{"id":"00:00:00:00:00:00:00:01","type":"OF"},"ingressPort":"1","protocol":1,"etherType":2048,"nwDst":"10.0.0.2","packet":"Io6q62g3mn66JKnjCABFAABUAABAAEABJqcKAAABCgAAAggAGFlGXgABELmmUwAAAAAVaA4AAAAAABAREhMUFRYXGBkaGxwdHh8gISIjJCUmJygpKissLS4vMDEyMzQ1Njc=","dlDst":"22:8E:AA:EB:68:37","nwSrc":"10.0.0.1","dlSrc":"9A:7E:BA:24:A9:E3"}
        //
        // Thus, we can use the "packet" field to re-construct the raw packet data from Base64-encoding:
        String packetInBase64 = "Io6q62g3mn66JKnjCABFAABUAABAAEABJqcKAAABCgAAAggAGFlGXgABELmmUwAAAAAVaA4AAAAAABAREhMUFRYXGBkaGxwdHh8gISIjJCUmJygpKissLS4vMDEyMzQ1Njc=";
        byte[] packetData = DatatypeConverter.parseBase64Binary(packetInBase64);
        // Now we can use the OpenDaylight classes to get Java objects for this packet:
        Ethernet ethPkt = new Ethernet();
        try {
            ethPkt.deserialize(packetData, 0, packetData.length*8);
        } catch (Exception e) {
            System.err.println("Failed to decode packet");
            die(-1);
        }
        if (ethPkt.getEtherType() == 0x800) {
            IPv4 ipv4Pkt = (IPv4) ethPkt.getPayload();
            // We could go on parsing layer by layer ... but you got the idea already I think.
            // So let's make some change to the packet to make it more interesting:
            InetAddress newDst = null;
            try {
                newDst = InetAddress.getByName("10.0.0.2");
            } catch (UnknownHostException e) {
                die(-1);
            }
            assert(newDst != null);
            ipv4Pkt.setDestinationAddress(newDst);
        }
        // Now we can get the binary data of the new packet to be forwarded.
        byte[] pktBinary = null;
        try {
            pktBinary = ethPkt.serialize();
        } catch (PacketException e1) {
            System.err.println("Failed to serialize packet");
            die(-1);
        }
        assert(pktBinary != null);
        
        // Encode packet to Base64 textual representation to be sent in JSON.
        String pktBase64 = DatatypeConverter.printBase64Binary(pktBinary);
        
        // We need to tell the packet forwarder, which node should forward the packet.
        // In OpenDaylight, a node is identified by node id and node type (like "OF" for OpenFlow).
        // For a list of all node attributes, cf. class NodeAttributes.
        // For a list of possible node types, cf. class NodeAttributes.TypeValues.
        JSONObject nodeJson = new JSONObject();
        String nodeId = "00:00:00:00:00:00:00:01";
        nodeJson.put(NodeAttributes.Keys.ID.toJSON(), nodeId);
        nodeJson.put(NodeAttributes.Keys.TYPE.toJSON(), NodeAttributes.TypeValues.OF.toJSON());
        
        // Create a packet forwarding request in JSON representation.
        // All attributes are described in class PacketForwarderRequestAttributes.
        JSONObject packetFwdRequestJson = new JSONObject();
        packetFwdRequestJson.put(PacketForwarderRequestAttributes.Keys.NODE.toJSON(), nodeJson);
        packetFwdRequestJson.put(PacketForwarderRequestAttributes.Keys.EGRESS_PORT.toJSON(), 1);
        packetFwdRequestJson.put(PacketForwarderRequestAttributes.Keys.PACKET.toJSON(), pktBase64);
        
        // Send the request by posting it to the packet forwarder queue.
        
        System.out.println("Sending packet forwarding request: ");
        System.out.println(packetFwdRequestJson.toString());
        
        try {
            TextMessage msg = session.createTextMessage();
            msg.setText(packetFwdRequestJson.toString());
            sender.send(msg);
        } catch (JMSException e) {
            System.err.println(e.getMessage());
            die(-1);
        }
            
        die(0);
    }

}
