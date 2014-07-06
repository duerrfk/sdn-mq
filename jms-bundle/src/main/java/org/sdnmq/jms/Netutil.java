/**
 * NetUtil
 * Copyright (c) 2014 Frank Duerr
 *
 * NetUtil is part of SDN-MQ. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.sdnmq.jms;

import java.util.StringTokenizer;

/**
 * Network helper functions.
 * 
 * @author Frank Duerr
 */
public class Netutil {
    /**
     * Converts a MAC address to colon hexadecimal representation (e.g., "00:01:02:03:04:05").
     */
    static public String macToStr(byte[] mac) {
        StringBuilder strBuilder = new StringBuilder();
        
        for (int i = 0; i < mac.length; i++) {
            strBuilder.append(String.format("%02X", mac[i]));
            if (i < mac.length-1) {
                strBuilder.append(":");
            }
        }
        return strBuilder.toString();
    }
    
    /**
     * Converts an IPv4 address from integer representation to dotted decimal notation (e.g., "192.168.1.1").
     * @param ipv4 the IPv4 address to be converted (must be in big endian byte-order)
     * @return dotted decimal notation
     */
    static public String ipv4ToStr(int ipv4) {
        short[] decimals = new short[4];
        
        for (int i = 0; i < 4; i++) {
            decimals[i] = (short) (ipv4&0xff);
            ipv4 >>= 8;
        }
        
        String dottedStr = Short.toString(decimals[3]) + "." + Short.toString(decimals[2]) + "." + 
                Short.toString(decimals[1]) + "." + Short.toString(decimals[0]);
        
        return dottedStr;
    }
    
    /**
     * Converts an IPv4 address from integer to a 32 bit binary representation (e.g., "11100100...").
     * @param ipv4 the IPv4 address to be converted (must be in big endian byte-order)
     * @return dotted decimal notation
     */
    static public String ipv4ToBinaryStr(int ipv4) {
        StringBuilder[] strBuilder = new StringBuilder[4];
        
        for (int i = 0; i < 4; i++) {
            strBuilder[i] = new StringBuilder(0);
            short b = (short) (ipv4&0xff);
            ipv4 >>= 8;
        
            short mask = (short) 128;
            for (int j = 0; j < 8; j++) {
                if ((b&mask) == mask) {
                    strBuilder[i].append('1');
                } else {
                    strBuilder[i].append('0');
                }
                
                mask >>= 1;
            }
        }
        
        return (strBuilder[3].toString()+strBuilder[2].toString()+strBuilder[1].toString()+strBuilder[0].toString());
    }
    
    /**
     * Parses a data link layer address in colon hexadecimal notation
     * @param addr the address string
     * @return address bytes or null if address string is invalid.
     */
    public static byte[] parseDlAddr(String addr) {
        assert(addr != null);
        
        StringTokenizer tokenizer = new StringTokenizer(addr, ":");
        int byteCnt = tokenizer.countTokens();
        if (byteCnt <= 0) {
            return null;
        }
        
        byte[] bytes = new byte[byteCnt];
        for (int i = 0; i < byteCnt; i++) {
           String token = tokenizer.nextToken();
           try {
               bytes[i] = Byte.parseByte(token, 16);
           } catch (NumberFormatException e) {
               return null;
           }
        }
        
        return bytes;
    }
}
