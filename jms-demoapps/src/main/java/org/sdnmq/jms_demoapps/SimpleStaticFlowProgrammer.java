/**
 * SimpleStaticFlowProgrammer
 * Copyright (c) 2014 Frank Duerr
 *
 * SimpleStaticFlowProgrammer is part of SDN-MQ. This program and the accompanying materials 
 * are made available under the terms of the Eclipse Public License v1.0 which 
 * accompanies this distribution, and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.sdnmq.jms_demoapps;

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

import org.json.JSONArray;
import org.json.JSONObject;
import org.sdnmq.jms.json.ActionAttributes;
import org.sdnmq.jms.json.FlowAttributes;
import org.sdnmq.jms.json.FlowProgrammerRequestAttributes;
import org.sdnmq.jms.json.MatchAttributes;
import org.sdnmq.jms.json.NodeAttributes;

/**
 * Demo applications for SDN-MQ showing how to use the flow programmer.
 * 
 * For a general introduction to JMS, you might want to read this tutorial:
 * http://docs.oracle.com/javaee/1.3/jms/tutorial/
 * 
 * @author Frank Duerr
 */
public class SimpleStaticFlowProgrammer {
    static final String FLOWPROGRAMMER_QUEUE_NAME = "org.sdnmq.flowprogrammer";
    
    private static Context ctx = null;
    private static QueueConnectionFactory queueFactory = null;
    private static QueueConnection connection = null;
    private static QueueSession session = null;
    private static Queue flowProgrammerQueue = null;
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
            flowProgrammerQueue = (Queue) ctx.lookup(FLOWPROGRAMMER_QUEUE_NAME);
        } catch(NameNotFoundException e) {
            System.err.println(e.getMessage());
            die(-1);
        } catch (NamingException e) {
            System.err.println(e.getMessage());
            die(-1);
        }
        
        try {
            sender = session.createSender(flowProgrammerQueue);
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
        
        // First, create the flow specification as JSON object.
        // A flow consists of a match, set of actions, and priority.        
        // The match: for a list of all possible match attributes, cf. class MatchAttributes.
        JSONObject matchJson = new JSONObject();
        String inPort = "1";
        matchJson.put(MatchAttributes.Keys.INGRESS_PORT.toJSON(), inPort);
        
        // The action: for a list of all possible action attributes, cf. class ActionAttributes. 
        JSONObject actionJson = new JSONObject();
        actionJson.put(ActionAttributes.Keys.ACTION.toJSON(), ActionAttributes.ActionTypeValues.DROP.toJSON());
        
        // A flow can have a list of different actions. We specify a list of actions
        // as JSON array (here, the array only contains one action).
        JSONArray actionsJson = new JSONArray();
        actionsJson.put(actionJson);
        
        // The flow consists of a match specification, action specification, and priority
        // (cf. class FlowAttributes)
        JSONObject flowJson = new JSONObject();
        flowJson.put(FlowAttributes.Keys.MATCH.toJSON(), matchJson);
        flowJson.put(FlowAttributes.Keys.ACTIONS.toJSON(), actionsJson);
        flowJson.put(FlowAttributes.Keys.PRIORITY.toJSON(), 0);
        
        // We need to tell the flow programmer, which node to program.
        // In OpenDaylight, a node is identified by node id and node type (like "OF" for OpenFlow).
        // For a list of all node attributes, cf. class NodeAttributes.
        // For a list of possible node types, cf. class NodeAttributes.TypeValues.
        
        JSONObject nodeJson = new JSONObject();
        String nodeId = "00:00:00:00:00:00:00:01";
        nodeJson.put(NodeAttributes.Keys.ID.toJSON(), nodeId);
        nodeJson.put(NodeAttributes.Keys.TYPE.toJSON(), NodeAttributes.TypeValues.OF.toJSON());
        
        // Create the FlowProgrammer request in JSON representation.
        // To add a flow, we need to specify the command, the flow, and the node to be programmed
        // (cf. class FlowProgrammerRequestAttributes).
        
        JSONObject addRequestJson = new JSONObject();
        // All possible commands are specified in FlowProgrammerRequestAttributes.CommandValues
        addRequestJson.put(FlowProgrammerRequestAttributes.Keys.COMMAND.toJSON(), 
                FlowProgrammerRequestAttributes.CommandValues.ADD.toJSON());
        String flowName = "DemoFlow";
        addRequestJson.put(FlowProgrammerRequestAttributes.Keys.FLOW_NAME.toJSON(), flowName);
        addRequestJson.put(FlowProgrammerRequestAttributes.Keys.FLOW.toJSON(), flowJson);
        addRequestJson.put(FlowProgrammerRequestAttributes.Keys.NODE.toJSON(), nodeJson);
        
        // Program the flow by sending the request to the flow programmer queue.
        
        System.out.println("Programming flow with following request: ");
        System.out.println(addRequestJson.toString());
        
        try {
            TextMessage msg = session.createTextMessage();
            msg.setText(addRequestJson.toString());
            sender.send(msg);
        } catch (JMSException e) {
            System.err.println(e.getMessage());
            die(-1);
        }
        
        // Delete the flow again after 10 s.
        
        System.out.println("Waiting 30 s ...");
        
        try {
            Thread.sleep(30000);
        } catch (InterruptedException e) {}
        
        // A delete request just contains the flow name to be deleted together with the delete command.
        
        JSONObject deleteRequestJson = new JSONObject();
        deleteRequestJson.put(FlowProgrammerRequestAttributes.Keys.COMMAND.toJSON(), 
                FlowProgrammerRequestAttributes.CommandValues.DELETE.toJSON());
        deleteRequestJson.put(FlowProgrammerRequestAttributes.Keys.FLOW_NAME.toJSON(),
                flowName);
        
        // Delete the flow by sending the delete request to the flow programmer queue.
        
        System.out.println("Deleting flow with the following request: ");
        System.out.println(deleteRequestJson.toString());
        
        try {
            TextMessage msg = session.createTextMessage();
            msg.setText(deleteRequestJson.toString());
            sender.send(msg);
        } catch (JMSException e) {
            System.err.println(e.getMessage());
            die(-1);
        }
        
        die(0);
    }

}
