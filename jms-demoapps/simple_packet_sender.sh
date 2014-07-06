#!/bin/sh

# These JARs are downloaded automatically to the local
# repository ($HOME/m2/repository) by Maven when building the
# demos apps.
SDNMQ="$HOME/.m2/repository/org/sdnmq/sdnmq-jms/0.1-SNAPSHOT/sdnmq-jms-0.1-SNAPSHOT.jar"
SAL="$HOME/.m2/repository/org/opendaylight/controller/sal/0.7.0/sal-0.7.0.jar"
JSON="$HOME/.m2/repository/org/json/json/20131018/json-20131018.jar"
JAXBAPI="$HOME/.m2/repository/javax/xml/bind/jaxb-api/2.2.4/jaxb-api-2.2.4.jar"
ACTIVEMQ="$HOME/.m2/repository/org/apache/activemq/activemq-all/5.9.0/activemq-all-5.9.0.jar"
APACHE_COMMONS_LANG3="$HOME/.m2/repository/org/apache/commons/commons-lang3/3.1/commons-lang3-3.1.jar"

DEMOAPPS="./target/jms-demoapps-0.1-SNAPSHOT.jar"

# We add the directory "./src/main/java" to the classpath,
# so the JNDI properties file "jndi.properties" can be found.
JNDIPROPS="./src/main/java"

java -cp "$APACHE_COMMONS_LANG3":"$SDNMQ":"$SAL":"$JSON":"$JAXBAPI":"$ACTIVEMQ":"$DEMOAPPS":"$JNDIPROPS" org.sdnmq.jms_demoapps.SimplePacketSender
