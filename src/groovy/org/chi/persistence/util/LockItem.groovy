package org.chi.persistence.util

import org.chi.persistence.XmlSerializer
import org.chi.util.Log

/**
 * Lock a persistence object
 * @author rgrey
 */
class LockItem {

    private static final List locks = []
    private static final pattern = /<([a-z,1-9,-]*).*id="([0-9]*)".*>/
    private static final tkeys = new ThreadLocal<List>()

    /**
     * Retrieve lock if no unique keys are currently locked
     * @param obj
     * @param serial
     * @param p priority in milliseconds
     * @return refreshed object
     */
    public static Object lock(Object obj, XmlSerializer serial, int p=1500) {
        try {
            if (! obj || ! obj.id) return obj
            while (! getlock(keys(obj, serial))) Thread.sleep(p)
            String name = Thread.currentThread().name
            Log.debug "LockItem.lock() : lock on ${name}"
            // refresh from db, just in case
            serial.session.evict(obj)
            return serial.session.get(obj.class, obj.id)
        } finally {
            serial.beginTransaction()
        }
    }

    /** 
     * Unlock elements from object
     * @param serial
     */
    public static unlock(XmlSerializer serial) {
        try {
            serial.endTransaction();
            serial.closeSession();
        } finally {
            Log.debug "LockItem.unlock() : ${locks.size()} current"
            if (! tkeys.get()) return
            String tname = Thread.currentThread().name
            Log.debug "LockItem.unlock() : unlocked on ${tname}"
            locks.removeAll(tkeys.get())
            Log.debug "LockItem.unlock() : ${locks.size()} remain"
            tkeys.remove()
        }
    }

    /**
     * Generate a list of key/types for this object (i.e. "survey1234","page4567")
     * @param obj
     * @param serial
     * @return
     */
    private static List keys(Object obj, XmlSerializer serial) {
        def r = serial.serialize(obj) =~ pattern
        long timing = System.currentTimeMillis()
        def result = []
        while (r.find()) { result.add(r.group(1) + r.group(2)) }
        tkeys.set(result)
        return result
    }

    /**
     * Syncronized check method
     * @param keys
     * @return
     */
    private static synchronized getlock(List keys) {
        if (keys.intersect(locks)) return false
        locks.addAll(keys)
        return true
    }

}
