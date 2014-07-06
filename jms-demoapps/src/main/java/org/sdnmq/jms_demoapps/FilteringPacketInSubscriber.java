/**
 * FilteringPacketInSubscriber
 * Copyright (c) 2014 Frank Duerr
 *
 * FilteringPacketInSubscriber is part of SDN-MQ. This program and the accompanying materials 
 * are made available under the terms of the Eclipse Public License v1.0 which 
 * accompanies this distribution, and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.sdnmq.jms_demoapps;

import java.util.Enumeration;

import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageListener;
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

import org.json.JSONException;
import org.json.JSONObject;
import org.sdnmq.jms.MessageFilterAttributes;

/**
 * Demo application for SDN-MQ showing how to filter packet-in events using packet attributes.
 * 
 * For a general introduction to JMS, you might want to read this tutorial:
 * http://docs.oracle.com/javaee/1.3/jms/tutorial/
 * 
 * @author Frank Duerr
 */
public class FilteringPacketInSubscriber implements MessageListener {
    // Default JNDI name of the packet-in topic object
    // (can be changed in the OpenDaylight configuration $OPENDAYLIGHTHOME/configuration/config.ini).
    static final String PACKETIN_TOPIC_NAME = "org.sdnmq.packetin";
    static final int IPV4_ETHERTYPE = 0x0800;
    
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
    
    private static void printMsgProperties(Message msg) {
        try {
            Enumeration keys = msg.getPropertyNames();
            while (keys.hasMoreElements()) {
                String key = (String) keys.nextElement();
                Object value = msg.getObjectProperty(key);
                System.out.println(key + " " + value.toString());
            }
        } catch (JMSException e) {
            System.err.println(e.getMessage());
        }
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
            // This selector filters messages from the IPv4 source address 10.0.0.1.
            //
            // For a full list of filter attributes, cf. class MessageFilterAttributes.
            //
            // For a description of the JMS selector concept, the WebSphere MQ documentation below gives a good overview:
            // http://publib.boulder.ibm.com/infocenter/wmqv6/v6r0/index.jsp?topic=%2Fcom.ibm.mq.csqzaw.doc%2Fuj25420_.htm
            //
            // Hint: You can use the binary address attributes NW_SRC_BINARY and NW_DST_BINARY
            // together with the LIKE operator to match a subnet prefix as used by CIDR.
            String selector = MessageFilterAttributes.Keys.DL_TYPE.toFilterName() + "=" + IPV4_ETHERTYPE + " AND " +
                    MessageFilterAttributes.Keys.NW_SRC.toFilterName() + "='10.0.0.1'";
            subscriber = session.createSubscriber(packetinTopic,  selector,  false);
            // The listener implements the callback function onMessage(),
            // which is called whenever a message is received.
            subscriber.setMessageListener(new FilteringPacketInSubscriber());
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
        
        // Message handling is done asynchronously in the onMessage() callback.
        // The main thread can sleep meanwhile.
        
        while (true) {
            try {
                Thread.sleep(10000);
            } catch (InterruptedException e) {
            }
        }
    }

    @Override
    public void onMessage(Message msg) {
        if (msg instanceof TextMessage) {
            String messageStr = null;
            try {
                messageStr = ((TextMessage) (msg)).getText();
            } catch (JMSException e) {
               System.err.println(e.getMessage());
            }
            
            // Parse JSON message payload
            
            JSONObject json = null;
            try {
                json = new JSONObject(messageStr);
                // Print the JSON message to show how it looks like.
                System.out.println(json.toString());
                System.out.println();
                // Print all message attributes. These attributes can be used for
                // message filtering.
                printMsgProperties(msg);
            } catch (JSONException e) {
                System.err.println(e.getMessage());
            }
        }
    }

}
