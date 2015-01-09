/*
 * Copyright (C) 2014 Turkcell
 * 
 * Created by Can Ciloglu on 14 Tem 2014
 *
 */
package com.turkcell.curio.utils;

import java.util.UUID;

/**
 * UUID Generator class. Particularly used for generating time-based UUID.
 * 
 * @author Can Ciloglu
 *
 */
public class UUIDGenerator
{
	/**
	 * Offset for converting Epoch time to 15 October 1582 00:00:000000000
	 */
	private static final long TIME_OFFSET = 122192928000000000l;
	private static final long MS_NANO = 10000l;
	
    /**
     * Creates a type 1 UUID (time-based UUID) with the timestamp of @param rawTimestamp, in milliseconds.
     *
     * @return a UUID instance
     */
    public static UUID generateTimeBasedUUID(long rawTimestamp)
    {
    	long modifiedTimeStamp = (rawTimestamp * MS_NANO) + TIME_OFFSET;
    	
        // Time field components are hi, low, mid
        int clockHi = (int) (modifiedTimeStamp >>> 32);
        int clockLo = (int) modifiedTimeStamp;
        int midhi = (clockHi << 16) | (clockHi >>> 16);
        
        // need to squeeze in type (4 MSBs in byte 6, clock hi)
        midhi &= ~0xF000; // remove high nibble of 6th byte
        midhi |= 0x1000; // type 1
        long midhiL = (long) midhi;
        midhiL = ((midhiL << 32) >>> 32); // to get rid of sign extension
        // and reconstruct
        long mostSignificantBits = (((long) clockLo) << 32) | midhiL;
        
        return new UUID(mostSignificantBits, UUID.randomUUID().getLeastSignificantBits());
    }
}