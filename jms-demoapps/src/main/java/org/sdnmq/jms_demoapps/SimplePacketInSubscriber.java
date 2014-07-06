/**
 * SimplePacketInSubscriber
 * Copyright (c) 2014 Frank Duerr
 *
 * SimplePacketInSubscriber is part of SDN-MQ. This program and the accompanying 
 * materials are made available under the terms of the Eclipse Public License v1.0 
 * which accompanies this distribution, and is available at 
 * http://www.eclipse.org/legal/epl-v10.html
 */

package org.sdnmq.jms_demoapps;

import java.net.InetAddress;
import java.net.UnknownHostException;

import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.jms.Topic;
import javax.jms.TopicConnection;
import javax.jms.TopicConnectionFactory;
import javax.jms.TopicSession;
import javax.jms.TopicSubscriber;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NameNotFoundException;
import javax.naming.NamingException;
import javax.xml.bind.DatatypeConverter;

import org.json.JSONException;
import org.json.JSONObject;
import org.sdnmq.jms.json.NodeAttributes;
import org.sdnmq.jms.json.PacketInAttributes;

/**
 * Demo application of SDN-MQ showing how to subscribe to JMS packet-in events using SDN-MQ.
 * 
 * For a general introduction to JMS, you might want to read this tutorial:
 * http://docs.oracle.com/javaee/1.3/jms/tutorial/
 *  
 * Also have a look at file jndi.properties for the setup of the Java Naming Service (JNS).
 * 
 * @author Frank Duerr
 *
 */
public class SimplePacketInSubscriber {
    // Default JNDI name of the packet-in topic object
    // (can be changed in the OpenDaylight configuration $OPENDAYLIGHTHOME/configuration/config.ini).
    static final String PACKETIN_TOPIC_NAME = "org.sdnmq.packetin";
    
    private static Context ctx = null;
    private static TopicConnectionFactory queueFactory = null;
    private static TopicConnection connection = null;
    private static TopicSession session = null;
    private static TopicSubscriber subscriber = null;
    private static Topic packetinTopic = null;
    
    private static void die(int status) {
        if (subscriber != null) {
            try {
                subscriber.close();
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
            queueFactory = (TopicConnectionFactory) ctx.lookup("TopicConnectionFactory");
        } catch (NamingException e) {
            System.err.println(e.getMessage());
            die(-1);
        }
        
        try {
            connection = queueFactory.createTopicConnection();
        } catch (JMSException e) {
            System.err.println(e.getMessage());
            die(-1);
        }
        
        try {
            session = connection.createTopicSession(false, Session.AUTO_ACKNOWLEDGE);
        } catch (JMSException e) {
            System.err.println(e.getMessage());
            die(-1);
        }
        
        try {
            packetinTopic = (Topic) ctx.lookup(PACKETIN_TOPIC_NAME);
        } catch(NameNotFoundException e) {
            System.err.println(e.getMessage());
            die(-1);
        } catch (NamingException e) {
            System.err.println(e.getMessage());
            die(-1);
        }
         
        try {
            subscriber = session.createSubscriber(packetinTopic);
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
        
        // Wait for packet-in events.
        
        while (true) {
            try {
                // Block until a packet-in event is received.
                // Another alternative is to define a callback function
                // (cf. example FilteringPacketInSubscriber).
                Message msg = subscriber.receive();
                if (msg instanceof TextMessage) {
                    String messageStr = ((TextMessage) (msg)).getText();
                    
                    // Parse JSON message payload
                    
                    JSONObject json = null;
                    try {
                        json = new JSONObject(messageStr);
                        // Print the JSON message to show how it looks like.
                        System.out.println(json.toString());
                        System.out.println();
                    } catch (JSONException e) {
                        System.err.println(e.getMessage());
                        continue;
                    }
                    
                    // Search for some packet attributes (for a list of all possible keys, see class PacketInAttributes).
                    
                    try {
                        int ethertype = json.getInt(PacketInAttributes.Keys.ETHERTYPE.toJSON());
                        System.out.println("Ethertype: " + ethertype);
                        String macSrcAddr = json.getString(PacketInAttributes.Keys.DL_SRC.toJSON());
                        System.out.println("Source MAC address: " + macSrcAddr);
                        if (json.has(PacketInAttributes.Keys.NW_SRC.toJSON())) {
                            InetAddress nwSrcAddr;
                            try {
                                nwSrcAddr = InetAddress.getByName(PacketInAttributes.Keys.NW_SRC.toJSON());
                                System.out.println(nwSrcAddr);
                            } catch (UnknownHostException e) {
                                System.err.println("Invalid source IP address: " + e.getMessage());
                            }
                        }
                        JSONObject nodeJson = json.getJSONObject(PacketInAttributes.Keys.NODE.toJSON());
                        String nodeId = nodeJson.getString(NodeAttributes.Keys.ID.toJSON());
                        System.out.println("Node (switch): " + nodeId);
                        String inport = json.getString(PacketInAttributes.Keys.INGRESS_PORT.toJSON());
                        System.out.println("Ingress port: " + inport);
                    } catch (JSONException e) {
                        System.err.println(e.getMessage());
                    }
                    
                    // If you want to use your own packet parser, you can also get
                    // the raw packet data.
                    
                    String packetDataBase64 = json.getString(PacketInAttributes.Keys.PACKET.toJSON());
                    byte[] packetData = DatatypeConverter.parseBase64Binary(packetDataBase64);
                    // Add your own custom packet parsing here ... we just print the raw data here.
                    for (int i = 0; i < packetData.length; i++) {
                        if (i%16 == 0) {
                            System.out.println();
                        }
                        System.out.print(String.format("%02X ", packetData[i]) + " ");
                    }
                    System.out.println();
                }
            } catch (JMSException e) {
                System.err.println(e.getMessage());
            }
        }
    }

}
