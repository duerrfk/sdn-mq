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

Please have a look at file

jms-demoapps/src/main/java/org/sdnmq/jms_demoapps/SimpleStaticFlowProgrammer.java

from the source code distribution.

Packet Forwarding
-----------------

Please have a look at file

jms-demoapps/src/main/java/org/sdnmq/jms_demoapps/SimplePacketSender.java

from the source code distribution.

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

???

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

3. SDN-MQ source code from GitHub

    $ git clone https://github.com/duerrfk/SDN-MQ.git

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
