/**
 * JNDIHelper
 * Copyright (c) 2014 Frank Duerr
 *
 * JNDIHelper is part of SDN-MQ. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.sdnmq.jms;

import java.util.Iterator;
import java.util.Properties;
import java.util.Set;

/**
 * This class supports the setup of JNDI.
 * 
 * @author Frank Duerr
 */
public class JNDIHelper {
    private static final String JNDI_PROPERTIES_PREFIX = "sdnmq.jndi.";
    
    /**
     * Retrieves the JNDI properties from the OpenDaylight system properties.
     * (see file $OPENDAYLIGHTHOME/configuration/config.ini).
     * 
     * Every property starting with JNDI_PROPERTIES_PREFIX is interpreted as JNDI property
     * (after removing this prefix).
     */
    static public Properties getJNDIProperties() {
        Properties jndiProperties = new Properties();
        Properties sysProperties = System.getProperties();
        Set<String> keys = sysProperties.stringPropertyNames();
        
        for (Iterator<String> it = keys.iterator(); it.hasNext(); ) {
            String key = it.next();
            if (key.startsWith(JNDI_PROPERTIES_PREFIX)) {
                String jndiKey = key.substring(JNDI_PROPERTIES_PREFIX.length());
                String value = sysProperties.getProperty(key);
                jndiProperties.setProperty(jndiKey, value);
            }
            
        }
        
        return jndiProperties;
    }
}
