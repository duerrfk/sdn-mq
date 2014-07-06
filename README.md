SDN-MQ is a Java Messaging Service (JMS) northbound interface for the
OpenDaylight SDN controller.

Using SDN-MQ has several advantages:

1. SDN-MQ allows for receiving packet-in events without the need to
implement OSGi services. This reduces the complexity of implementing your
own SDN services without sacrificing flexibility.

2. Packet-in events can be filtered using standard JMS selectors. So
your network control logic only sees what it is really interested in.

3. SDN-MQ consequently uses simple textual message formats based on
JSON, making message interpretation and generation straightforward. So
you get the simplicitly of popular JSON/HTTP-based APIs, and you'll
still be able to receive packet-in events (which HTTP interfaces don't
support due to the request/response nature of HTTP).  

4. Since SDN-MQ is based on textual JSON messages, it is accessible
through the Streaming Text Oriented Messaging Protocol (STOMP), e.g.,
using Apache ActiveMQ. This not only allows for Java-JMS clients but
also clients implemented in C, Ruby, Perl, Python, PHP,
ActionScript/Flash, Smalltalk, etc. (everything supported by your JMS
server).

5. SDN-MQ supports essential SDN controller functionalities,
namely, packet-in events, flow programming, and packet forwarding.

How to use SDN-MQ
=================

Examples are certainly the best way to show how SDN-MQ works. For
the complete source code of the following examples, please have a look
at the sample applications provided together with SDN-MQ located in
the folder jms-demoapps.

Receiving Packet-in Events
--------------------------

    while (true) {
        try {
            // Block until a packet-in event is received.
            // Another alternative is to define a callback
            // function (cf. example FilteringPacketInSubscriber).
            Message msg = subscriber.receive();
            if (msg instanceof TextMessage) {
                String messageStr = ((TextMessage) (msg)).getText();
                // Parse JSON message payload
                JSONObject json = null;
                try {
                    json = new JSONObject(messageStr);
                    // Print the JSON message to show how it looks
                    // like.
                    System.out.println(json.toString());
                    System.out.println();
                } catch (JSONException e) {
                    System.err.println(e.getMessage());
                    continue;
                }
                // Access some message attributes
                try {
                    int ethertype = json.getInt(PacketInAttributes.Keys.ETHERTYPE.toJSON());
                } catch (JSONException e) {
                    System.err.println(e.getMessage());
                }
            }
        }    
    }

Filtering Packet-in Events Using JMS Selectors
----------------------------------------------

Packet-in events can be filters using JMS selectors like this one (you
better should use the pre-defined enumerations, however, this plain
text here better shows the idea):

    String selector = "etherType=0x0800 AND nwSrc='10.0.0.1'";
    subscriber = session.createSubscriber(packetinTopic, selector,  false);

Prefix matches are also supported through the LIKE operator and binary
address attributes!
 
Flow Programming
----------------

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

Packet Forwarding
-----------------

    // Create packet using OpenDaylight packet classes (or any other packet parser/constructor you like most)
    // In most use cases, you will not create the complete packet yourself from scratch
    // but rather use a packet received from a packet-in event as basis. Let's assume that
    // the received packet-in event looks like this (in JSON representation as delivered by
    // the packet-in handler):
    //
    // {"node":{"id":"00:00:00:00:00:00:00:01","type":"OF"},"ingressPort":"1","protocol":1,
    // "etherType":2048,"nwDst":"10.0.0.2","packet":"Io6SOMEMOREBASE641Njc=",
    // "dlDst":"22:8E:AA:EB:68:37","nwSrc":"10.0.0.1","dlSrc":"9A:7E:BA:24:A9:E3"}
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

Installation
============

You need two things to use SDN-MQ:

1. A JMS server. 
2. An OpenDaylight instance with SDN-MQ service installed.

You can use any JMS server you like. As an example, we use Apache
ActiveMQ here. Although you might know best, how to install your
favorite JMS server, we quickly walk you through
the steps to install ActiveMQ below.

Then, we show the (few) steps required to install the SDN-MQ release
package, which includes a complete and pre-configured OpenDaylight
controller. Details to build and configure everything from the source
code are shown below.

JMS Setup (using Apache ActiveMQ)
---------------------------------

First, we need to install a JMS server. 

Installing ActiveMQ is straightforward. First, download ApacheMQ from here: 

http://activemq.apache.org/activemq-590-release.html

Install ActiveMQ to some directory you prefer:

    $ tar xzf ~/scratch/apache-activemq-5.9.0-bin.tar.gz

Make sure JAVA_HOME is set.

Start ApacheMQ:

    $ cd apache-activemq-5.9.0/bin
    $ ./activemq start

Now, you should be able to access the web-frontend of ActiveMQ with
your browser (user "admin"; password "admin"):

http://myhost:8161/admin/

Use this web interface to set up the message queues and topics
required by SDN-MQ using the following names (see below under
Configuration how to change these names if you do not like the default
names):

- Topic for receiving packet-in events: org.sdnmq.packetin 
- Queue for flow programming: org.sdnmq.flowprogrammer
- Queue for packet forwarding: org.sdnmq.packetout

Simply go to the menus "Queue" and "Topics", insert the names and
click "create".

Installing the SDN-MQ Release Package
-------------------------------------

The simplest way to install SDN-MQ is to use an SDN-MQ release package
including a complete OpenDaylight controller (Hyrogen release base
edition at the time of writing this document) with pre-configures
SDN-MQ service. 

Download the SDN-MQ release package from GitHub:

https://github.com/duerrfk/sdn-mq/releases

Install it to a folder on the same machine as ActiveMQ (of course, you
can also install it to a different machine, however, then you have to
change the references to "localhost" in the SDN-MQ part of the
OpenDaylight configuration; see below):

    $ tar xzf opendaylight-sdnmq-0.1.0.tgz

Now you can start OpenDaylight with the SDN-MQ service:

    $ cd opendaylight-sdnmq/
    $ ./run.sh

In order to test your installation, you can try out the client
examples described above, which come with the SDN-MQ source code.

Building SDN-MQ from Source Code
================================

To build SDN-MQ, you need the following things:

1. OpenDaylight SDN controller, e.g., the Hydrogen release:

http://www.opendaylight.org/software/downloads

2. Some libraries that SDN-MQ depends on to be loaded into the 
OpenDaylight OSGi framework:

- geronimo-jms_1.1_spec-1.1.1.jar
- activemq-osgi-5.9.0.jar
- geronimo-j2ee-management_1.1_spec-1.0.1.jar
- geronimo-blueprint-api-1.0.0.jar

You will find these libraries in public Maven repositories or alternatively
as part of the SDN-MQ release package (see above) in the folder
opendaylight-sdnmq/plugins/.

3. SDN-MQ source code from GitHub:

        $ git clone https://github.com/duerrfk/sdn-mq.git

5. Maven and Java to compile SDN-MQ (we assume that you have already
installed Maven and Java) 

First, build the SDN-MQ OSGi bundle:

    $ cd sdn-mq/jms-bundle/
    $ mvn package

Copy the created bundle into the plugins folder of OpenDaylight:

$ cp sdn-mq/jms-bundle/target/sdnmq-jms-0.1-SNAPSHOT.jar opendaylight/plugins/

Also copy the above JAR files into the plugins folder:

    $ cp geronimo-jms_1.1_spec-1.1.1.jar opendaylight/plugins/
    $ cp activemq-osgi-5.9.0.jar opendaylight/plugins/
    $ cp geronimo-j2ee-management_1.1_spec-1.0.1.jar opendaylight/plugins/
    $ cp geronimo-blueprint-api-1.0.0.jar opendaylight/plugins/

Edit the OpenDaylight configuration file as described in Section
"Configuration" below.

    $ nano opendaylight/configuration/config.ini

Now, SDN-MQ can be started together with OpenDaylight (run.sh).

To build the examples, execute the following commands to first install
the SDN-MQ JAR into the local Maven repository and the compile the
client applications:

    $ cd sdn-mq/jms-bundle/
    $ mvn install 
    $ cd sdn-mq/jms-demoapps/
    $ mvn package

Maven will download the required JAR files automatically (see folder
$HOME/.m2/repository/). 

Now, you can start the demo applications using the start scripts from
folder sdn-mq/jms-demoapps/.

Eclipse projects can be create from the source folders using 

    $ mvn eclipse:eclipse

Configuration
=============

SDN-MQ is integrated as an OSGi bundle into OpenDaylight. You can
change the settings like JMS queue and topic names or JNDI providers
by editing the OpenDaylight configuration file:

    $ nano opendaylight-sdnmq/configuration/config.ini

Every SDN-MQ configuration property has the prefix "sdnmq.". The
default configuration looks like this:

    # JNDI names of topic and queue objects.
    sdnmq.topicname.packetin=org.sdnmq.packetin
    sdnmq.queuename.packetout=org.sdnmq.packetout
    sdnmq.queuename.flowprogrammer=org.sdnmq.flowprogrammer
    
    # Everything after "sdnmq.jndi." will be interpreted as property of
    # the initial context of JNDI. Here, we are using the JNDI service
    # provided  by ActiveMQ. If you are using another JNDI service
    # implementation, you might have to adapt these settings.
    sdnmq.jndi.java.naming.factory.initial=org.apache.activemq.jndi.ActiveMQInitialContextFactory

    # The failover transport of ActiveMQ will automatically failover
    # between different servers in case of server crashes. If only one
    # server is defined, it automatically tries to reconnect to the
    # server.
    sdnmq.jndi.java.naming.provider.url=failover:(tcp://localhost:61616)?randomize=false
    
    # Configuration of the JNDI entries of SDN-MQ's topics and queues.
    # Everything after "sdnmq.jndi.topic." specifies the JNDI name of a
    # topic object as defined above. Everything after "sdnmq.jndi.queue."
    # specifies the JNDI name of a queue object as defined above.
    # The right-hand side specified the "physical" name as defined when 
    # creating the queue or topic in ActiveMQ (e.g., via the web
    # interface)
    sdnmq.jndi.topic.org.sdnmq.packetin=org.sdnmq.packetin
    sdnmq.jndi.queue.org.sdnmq.packetout=org.sdnmq.packetout
    sdnmq.jndi.queue.org.sdnmq.flowprogrammer=org.sdnmq.flowprogrammer
 
Future Work
===========

- Basic functionality like routing or ARP handling could be factored
  out from OpenDaylight into components using SDN-MQ.
- Implement event driven topology service through SDN-MQ. So far,
  topology information can be accessed using request/response through the
  OpenDaylight REST interface.
