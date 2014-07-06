/** 
 * FlowProgrammer
 * Copyright (c) 2014 Frank Duerr
 *
 * FlowProgrammer is part of SDN-MQ. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0, which accompanies this distribution
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.sdnmq.jms;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
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

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.opendaylight.controller.sal.action.Action;
import org.opendaylight.controller.sal.action.Controller;
import org.opendaylight.controller.sal.action.Drop;
import org.opendaylight.controller.sal.action.Loopback;
import org.opendaylight.controller.sal.action.Output;
import org.opendaylight.controller.sal.action.PopVlan;
import org.opendaylight.controller.sal.action.PushVlan;
import org.opendaylight.controller.sal.action.SetDlDst;
import org.opendaylight.controller.sal.action.SetDlSrc;
import org.opendaylight.controller.sal.action.SetNwDst;
import org.opendaylight.controller.sal.action.SetNwSrc;
import org.opendaylight.controller.sal.action.SetTpDst;
import org.opendaylight.controller.sal.action.SetTpSrc;
import org.opendaylight.controller.sal.core.Node;
import org.opendaylight.controller.sal.core.NodeConnector;
import org.opendaylight.controller.sal.flowprogrammer.Flow;
import org.opendaylight.controller.sal.flowprogrammer.IFlowProgrammerService;
import org.opendaylight.controller.sal.match.Match;
import org.opendaylight.controller.sal.match.MatchType;
import org.opendaylight.controller.sal.utils.EtherTypes;
import org.opendaylight.controller.sal.utils.Status;
import org.opendaylight.controller.switchmanager.ISwitchManager;
import org.sdnmq.jms.json.ActionAttributes;
import org.sdnmq.jms.json.FlowAttributes;
import org.sdnmq.jms.json.FlowProgrammerRequestAttributes;
import org.sdnmq.jms.json.MatchAttributes;
import org.sdnmq.jms.json.NodeAttributes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Flow Programmer service taking flow programming requests from a specific JMS queue and programming these
 * flows using OpenDaylight. 
 * 
 * @author Frank Duerr
 */
public class FlowProgrammer implements MessageListener {
    private static final Logger log = LoggerFactory.getLogger(FlowProgrammer.class);
    
    /**
     *  This property can be defined in the OpenDaylight configuration to change the 
     *  flow programmer request queue name (JNDI name of queue object).
     *  $OPENDAYLIGHTHOME/configuration/config.ini
     */
    private static final String FLOWPROGRAMMER_QUEUE_PROPERTY = "sdnmq.queuename.flowprogrammer";
    private static final String DEFAULT_FLOWPROGRAMMER_QUEUE_NAME = "org.sdnmq.flowprogrammer";
    
    private QueueConnection connection = null;
    private QueueSession session = null;
    private QueueReceiver receiver = null;
    private Queue flowProgrammerQueue = null;
    
    private ISwitchManager switchManager = null;
    private IFlowProgrammerService flowProgrammerService = null;
    
    private Map<String, Flow> flowNameToFlow = null;
    private Map<String, Node> flowNameToNode = null;
    
    /**
     * Called by the dependency manager if all the required
     * dependencies are satisfied.
     */
    public void init() {
        flowNameToFlow = new HashMap<String, Flow>();
        flowNameToNode = new HashMap<String, Node>();
        
        if (initMQ()) {
            startMsgListener();
        }
    }
    
    /**
     * JMS setup
     */
    private boolean initMQ() {
        log.trace("Setting up JMS ...");
        
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
        
        String queueName = System.getProperty(FLOWPROGRAMMER_QUEUE_PROPERTY, DEFAULT_FLOWPROGRAMMER_QUEUE_NAME);
        log.info("Using the following queue for flow programming requests: " + queueName);
        try {
            flowProgrammerQueue = (Queue) ctx.lookup(queueName);
        } catch (NamingException e) {
            log.error(e.getMessage());
            releaseMQ();
            return false;
        }
        
        try {
            receiver = session.createReceiver(flowProgrammerQueue);
        } catch (JMSException e) {
            log.error(e.getMessage());
            releaseMQ();
            return false;
        }
        
        log.trace("Setup JMS successfully");
        
        return true;
    }
    
    /**
     * Release JMS-related objects.
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
     * Starts the receiver thread receiving flow programming requests via JMS queue.
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

    /**
     * Callback called by OpenDaylight when Switch Manager Service is bound.
     */
    void setSwitchManagerService(ISwitchManager s) {
       log.trace("Set SwitchManagerService.");
     
       switchManager = s;
    }
     
    /**
     * Callback called by OpenDaylight when Switch Manager Service is unbound. 
     */
    void unsetSwitchManagerService(ISwitchManager s) {
        log.trace("Removed SwitchManagerService.");
     
        if (switchManager == s) {
            switchManager = null;
        }
    }
    
    /**
     * Callback called by OpenDaylight when Flow Programmer Service is bound.
     */
    void setFlowProgrammerService(IFlowProgrammerService s) {
        log.trace("Set FlowProgrammerService.");
     
        flowProgrammerService = s;
    }
     
    /**
     * Callback called by OpenDaylight when Flow Programmer Service is unbound.
     */
    void unsetFlowProgrammerService(IFlowProgrammerService s) {
        log.trace("Removed FlowProgrammerService.");
     
        if (flowProgrammerService == s) {
            flowProgrammerService = null;
        }
    }
    
    /**
     * Creates OpenDaylight Match object from JSON specification.
     * 
     * @param node the node on which the match will be performed
     * @param json the JSON document specifying the match attributes
     * @return Match object or null if the JSON specification was invalid
     */
    private Match matchFromJson(Node node, JSONObject json) throws JSONException {
        Match m = new Match();
        
        if (json.has(MatchAttributes.Keys.INGRESS_PORT.toJSON())) {
            NodeConnector connector = NodeConnector.fromStringNoNode(json.getString(MatchAttributes.Keys.INGRESS_PORT.toJSON()), node);
            if (connector == null) {
                log.error("Port does not exist on node " + node.toString());
                return null;
            } else {
                m.setField(MatchType.IN_PORT, connector);
            }
        }
        
        if (json.has(MatchAttributes.Keys.DL_DST.toJSON())) {
            byte[] dlSrc = Netutil.parseDlAddr(json.getString(MatchAttributes.Keys.DL_SRC.toJSON()));
            if (dlSrc == null) {
                log.error("Invalid DL source address: " + json.getString(MatchAttributes.Keys.DL_SRC.toJSON()));
                return null;
            } else {
                m.setField(MatchType.DL_SRC, dlSrc);    
            }
        }
        
        if (json.has(MatchAttributes.Keys.DL_DST.toJSON())) {
            byte[] dlDst = Netutil.parseDlAddr(json.getString(MatchAttributes.Keys.DL_DST.toJSON()));
            if (dlDst == null) {
                log.error("Invalid DL destination address: " + json.getString(MatchAttributes.Keys.DL_DST.toJSON()));
                return null;
            } else {
                m.setField(MatchType.DL_DST, dlDst);    
            }
        }
        
        if (json.has(MatchAttributes.Keys.DL_VLAN.toJSON())) {
            short vlan = (short) json.getInt(MatchAttributes.Keys.DL_VLAN.toJSON());
            m.setField(MatchType.DL_VLAN, vlan);
        }
        
        if (json.has(MatchAttributes.Keys.DL_VLAN_PRIORITY.toJSON())) {
            byte vlanPr = (byte) json.getInt(MatchAttributes.Keys.DL_VLAN_PRIORITY.toJSON());
            m.setField(MatchType.DL_VLAN_PR, vlanPr);
        }
        
        short ethertype;
        if (json.has(MatchAttributes.Keys.ETHERTYPE.toJSON())) {
            ethertype = (short) json.getInt(MatchAttributes.Keys.ETHERTYPE.toJSON());
            m.setField(MatchType.DL_TYPE, ethertype);
        }
        
        InetAddress nwSrcMask = null;
        if (json.has(MatchAttributes.Keys.NW_SRC_MASK.toJSON())) {            
            try {
                nwSrcMask = InetAddress.getByName(json.getString(MatchAttributes.Keys.NW_SRC_MASK.toJSON()));
            } catch (UnknownHostException e) {
                log.error("Invalid source network mask " + json.getString(MatchAttributes.Keys.NW_SRC_MASK.toJSON()));
                return null;
            }
        }
        
        if (json.has(MatchAttributes.Keys.NW_SRC.toJSON())) {        
            try {
                InetAddress nwSrc = InetAddress.getByName(json.getString(MatchAttributes.Keys.NW_SRC.toJSON()));
                if (nwSrcMask != null) {
                    m.setField(MatchType.NW_SRC, nwSrc, nwSrcMask);
                } else {
                    m.setField(MatchType.NW_SRC, nwSrc);
                }
            } catch (UnknownHostException e) {
                log.error("Invalid source network address " + json.getString(MatchAttributes.Keys.NW_SRC.toJSON()));
                return null;
            }
        }
        
        InetAddress nwDstMask = null;
        if (json.has(MatchAttributes.Keys.NW_DST_MASK.toJSON())) {            
            try {
                nwDstMask = InetAddress.getByName(json.getString(MatchAttributes.Keys.NW_DST_MASK.toJSON()));
            } catch (UnknownHostException e) {
                log.error("Invalid destination network mask " + json.getString(MatchAttributes.Keys.NW_DST_MASK.toJSON()));
                return null;
            }
        }
        
        if (json.has(MatchAttributes.Keys.NW_DST.toJSON())) {
            try {
                InetAddress nwDst = InetAddress.getByName(json.getString(MatchAttributes.Keys.NW_DST.toJSON()));
                if (nwDstMask != null) {
                    m.setField(MatchType.NW_DST, nwDst, nwDstMask);
                } else {
                    m.setField(MatchType.NW_DST, nwDst);
                }
            } catch (UnknownHostException e) {
                log.error("Invalid destination network address " + json.getString(MatchAttributes.Keys.NW_DST.toJSON()));
                return null;
            }
        }
                
        if (json.has(MatchAttributes.Keys.NW_TOS.toJSON())) {        
            byte tos = (byte) json.getInt(MatchAttributes.Keys.NW_TOS.toJSON());
            m.setField(MatchType.NW_TOS, tos);
        }
        
        boolean protocolDefined = false;
        byte protocol;
        if (json.has(MatchAttributes.Keys.PROTOCOL.toJSON())) {
            protocol = (byte) json.getInt(MatchAttributes.Keys.PROTOCOL.toJSON());
            m.setField(MatchType.NW_PROTO, protocol);
            protocolDefined = true;
        }
        
        if (json.has(MatchAttributes.Keys.TP_SRC.toJSON())) {            
            if (!protocolDefined) {
                log.error("Layer 4 information specified without defining protocol number");
                return null;
            }
            
            short tpSrc = (short) json.getInt(MatchAttributes.Keys.TP_SRC.toJSON());
            m.setField(MatchType.TP_SRC, tpSrc);
        }
        
        if (json.has(MatchAttributes.Keys.TP_DST.toJSON())) {            
            if (!protocolDefined) {
                log.error("Layer 4 information specified without defining protocol number");
                return null;
            }
            
            short tpDst = (short) json.getInt(MatchAttributes.Keys.TP_DST.toJSON());
            m.setField(MatchType.TP_DST, tpDst);
        }
        
        return m;
    }
    
    /**
     * Creates OpenDaylight Action object from JSON specification.
     * 
     * @param node the node (switch) receiving the action (required to define output action with valid node connector) 
     * @param actionJson the JSON object specifying the action
     * @return OpenDaylight action object
     */
    private Action parseAction(Node node, JSONObject actionJson) throws JSONException {
        String actionType = actionJson.getString(ActionAttributes.Keys.ACTION.toJSON());
        
        if (actionType.equals(ActionAttributes.ActionTypeValues.LOOPBACK.toJSON())) {
            return new Loopback();
        } else if (actionType.equals(ActionAttributes.ActionTypeValues.DROP.toJSON())) {
            return new Drop();
        } else if (actionType.equals(ActionAttributes.ActionTypeValues.CONTROLLER.toJSON())) {
            return new Controller();
        } else if (actionType.equals(ActionAttributes.ActionTypeValues.OUTPUT.toJSON())) {
            String connectorId = actionJson.getString(ActionAttributes.Keys.PORT.toJSON());
            NodeConnector conn = NodeConnector.fromStringNoNode(connectorId, node);
            if (conn == null) {
                log.error("Invalid node connector for output action: " + connectorId);
                return null;
            } else {
                return new Output(conn);
            }
        } else if (actionType.equals(ActionAttributes.ActionTypeValues.SET_DL_SRC.toJSON())) {
            String addrStr = actionJson.getString(ActionAttributes.Keys.DL_ADDRESS.toJSON());
            byte[] addr = Netutil.parseDlAddr(addrStr);
            if (addr == null) {
                log.error("Invalid DL address specified: " + addrStr);
                return null;
            } else {
                return new SetDlSrc(addr);
            }
        } else if (actionType.equals(ActionAttributes.ActionTypeValues.SET_DL_DST.toJSON())) {
            String addrStr = actionJson.getString(ActionAttributes.Keys.DL_ADDRESS.toJSON());
            byte[] addr = Netutil.parseDlAddr(addrStr);
            if (addr == null) {
                log.error("Invalid DL address specified: " + addrStr);
                return null;
            } else {
                return new SetDlDst(addr);
            }
        } else if (actionType.equals(ActionAttributes.ActionTypeValues.SET_NW_SRC.toJSON())) {
            String addrStr = actionJson.getString(ActionAttributes.Keys.NW_ADDRESS.toJSON());
            try {
                InetAddress addr = InetAddress.getByName(addrStr);
                return new SetNwSrc(addr);
            } catch (UnknownHostException e) {
                log.error("Invalid network address specified: " + addrStr);
                return null;
            }
        } else if (actionType.equals(ActionAttributes.ActionTypeValues.SET_NW_DST.toJSON())) {
            String addrStr = actionJson.getString(ActionAttributes.Keys.NW_ADDRESS.toJSON());
            try {
                InetAddress addr = InetAddress.getByName(addrStr);
                return new SetNwDst(addr);
            } catch (UnknownHostException e) {
                log.error("Invalid network address specified: " + addrStr);
                return null;
            }
        } else if (actionType.equals(ActionAttributes.ActionTypeValues.SET_TP_SRC.toJSON())) {
            String addrStr = actionJson.getString(ActionAttributes.Keys.TP_ADDRESS.toJSON());
            try {
                int port = Integer.parseInt(addrStr);
                return new SetTpSrc(port);
            } catch (NumberFormatException e) {
                log.error("Invalid transport layer address specified: " + addrStr);
                return null;
            }
        } else if (actionType.equals(ActionAttributes.ActionTypeValues.SET_TP_DST.toJSON())) {
            String addrStr = actionJson.getString(ActionAttributes.Keys.TP_ADDRESS.toJSON());
            try {
                int port = Integer.parseInt(addrStr);
                return new SetTpDst(port);
            } catch (NumberFormatException e) {
                log.error("Invalid transport layer address specified: " + addrStr);
                return null;
            }
        } else if (actionType.equals(ActionAttributes.ActionTypeValues.PUSH_VLAN.toJSON())) {
            // 3 bit Priority Code Point
            int pcp = actionJson.getInt(ActionAttributes.Keys.PCP.toJSON());
            // 1 bit Drop Eligible Indicator (aka cfi)
            int dei = actionJson.getInt(ActionAttributes.Keys.DEI.toJSON());
            // 12 bit VLAN ID
            int vlanId = actionJson.getInt(ActionAttributes.Keys.VLAN_ID.toJSON());;
            return new PushVlan(EtherTypes.VLANTAGGED, pcp, dei, vlanId);
        } else if (actionType.equals(ActionAttributes.ActionTypeValues.POP_VLAN.toJSON())) {
            return new PopVlan();
        } else {
            log.error("Unsupported action: " + actionType);
            return null;
        }
    }
    
    /**
     * Create OpenDaylight actions from JSON specifications.
     * 
     * @param node the node to be programmed (required for specifying valid output actions with correct node connectors)
     * @param actionsJson JSON array specifying actions
     * @return list of created OpenDaylight actions
     */
    private List<Action> actionsFromJson(Node node, JSONArray actionsJson) throws JSONException {
        List<Action> actions = new LinkedList<Action>();
        
        for (int i = 0; i < actionsJson.length(); i++) {
            JSONObject actionJson = actionsJson.getJSONObject(i);
            Action action = parseAction(node, actionJson);
            if (action == null) {
                log.error("Invalid action: " + actionJson.toString());
                return null;
            } else {
                actions.add(action);
            }
        }
        
        return actions;
    }

    @Override
    public void onMessage(Message msg) {
        log.trace("Received flow programming request");
        
        // TODO: Check, how we can send an error message to the requester using JMS if something goes wrong.
        
        if (!(msg instanceof TextMessage)) {
            log.error("Received invalid message type (not a text message).");
            return;
        }

        // Parse JSON message
        
        JSONObject json = null;
        try {
            json = new JSONObject(((TextMessage) msg).getText());
        } catch (JSONException e) {
            log.error(e.getMessage());
            return;
        } catch (JMSException e) {
            log.error(e.getMessage());
            return;
        }
        assert(json != null);
        log.trace(json.toString());
        
        // Get the command to be executed.
        
        String command = null;
        try {
            command = json.getString(FlowProgrammerRequestAttributes.Keys.COMMAND.toJSON());
        } catch (JSONException e) {
            log.error("No command specified: " + e.getMessage());
            return;
        }
        assert(command != null);
        
        // Get name of flow
        
        String flowName = null;
        try {
            flowName = json.getString(FlowProgrammerRequestAttributes.Keys.FLOW_NAME.toJSON());
        } catch (JSONException e) {
            log.error("No flow name specified: " + e.getMessage());
            return;
        }
        assert(flowName != null);
        
        // Get node, match, actions, and priority of flow to be programmed.
        
        Match match = null;
        List<Action> actions = null;
        JSONObject flowJson = null;
        Node node = null;
        short priority = 0;
        if (command.equals(FlowProgrammerRequestAttributes.CommandValues.ADD.toJSON()) || 
                command.equals(FlowProgrammerRequestAttributes.CommandValues.MODIFY.toJSON())) {
            try {
                flowJson = json.getJSONObject(FlowProgrammerRequestAttributes.Keys.FLOW.toJSON());
            } catch (JSONException e) {
                log.error("No flow object defined for request: " + e.getMessage());
                return;
            }
            
            String nodeId = null;
            String nodeType = null;
            try {
                JSONObject nodeJson = json.getJSONObject(FlowProgrammerRequestAttributes.Keys.NODE.toJSON());
                nodeId = nodeJson.getString(NodeAttributes.Keys.ID.toJSON());
                if (nodeJson.has(NodeAttributes.Keys.TYPE.toJSON())) {
                    nodeType = nodeJson.getString(NodeAttributes.Keys.TYPE.toJSON());
                } else {
                    nodeType = NodeAttributes.TypeValues.OF.toJSON();
                }
            } catch (JSONException e) {
                log.error("No node attributes specified: " + e.getMessage());
                return;
            }
        
            node = Node.fromString(nodeType, nodeId);
            if (node == null) {
                log.error("Invalid node id: " + nodeId);
                return;
            }
            
            try {
                JSONObject matchJson = flowJson.getJSONObject(FlowAttributes.Keys.MATCH.toJSON());
                match = matchFromJson(node, matchJson);      
                if (match == null) {
                    log.error("Could not parse match specification");
                    return;
                }
            } catch (JSONException e) {
                log.error("No match specification: " + e.getMessage());
                return;
            }
            
            try {
                JSONArray actionsJson = flowJson.getJSONArray(FlowAttributes.Keys.ACTIONS.toJSON());
                actions = actionsFromJson(node, actionsJson);
                if (actions == null) {
                    log.error("Could not parse (some) actions. Will not program flow.");
                    return;
                }
            } catch (JSONException e) {
                log.error("No actions specified: "+ e.getMessage());
                return;
            }
            
            try {
                if (flowJson.has(FlowAttributes.Keys.PRIORITY.toJSON())) {
                    priority = (short) flowJson.getInt(FlowAttributes.Keys.PRIORITY.toJSON());
                } else {
                    priority = 0;
                }
            } catch (JSONException e) {
                log.error("No flow priority specified: " + e.getMessage());
                return;
            }
        }
        
        // Execute command to add/modify/remove flow.
        
       if (command.equals(FlowProgrammerRequestAttributes.CommandValues.MODIFY.toJSON()) ||
               command.equals(FlowProgrammerRequestAttributes.CommandValues.ADD.toJSON())) {
            assert(match != null);
            assert(actions != null);
            assert(flowName != null);
            assert(node != null);
            
            synchronized (flowNameToFlow) {
                Flow newFlow = new Flow(match, actions);
                newFlow.setPriority(priority);
                Flow oldFlow = flowNameToFlow.get(flowName);
                Status status = null;
                if (oldFlow != null) {
                    // Old flow exists, so we modify it.
                    status = flowProgrammerService.modifyFlow(node, oldFlow, newFlow);
                } else {
                    // No flow with that name exists, so add it.
                    status = flowProgrammerService.addFlow(node, newFlow);
                }
                if (!status.isSuccess()) {
                    log.error("Could not add/modify flow: " + status.getDescription());
                    return;
                }
            
                flowNameToFlow.put(flowName, newFlow);
                flowNameToNode.put(flowName, node);
            }
        } else if (command.equals(FlowProgrammerRequestAttributes.CommandValues.DELETE.toJSON())) {
            assert(flowName != null);
            
            synchronized (flowNameToFlow) {
                Flow flow = flowNameToFlow.get(flowName);
                node = flowNameToNode.get(flowName);
                assert( flow == null || ((flow != null) && (node != null)) );
                
                if (flow != null) {
                    Status status = flowProgrammerService.removeFlow(node, flow);
                    if (!status.isSuccess()) {
                        log.error("Could not delete flow: " + status.getDescription());
                        return;
                    }   
                } else {
                    log.error("Flow to be deleted does not exist");
                    return;
                }
            }
        }
    }
    
}
