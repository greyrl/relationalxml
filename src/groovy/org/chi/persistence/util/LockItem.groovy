package org.chi.persistence.util

import org.chi.persistence.PersistUtils
import org.chi.persistence.XmlSerializer
import org.chi.util.Log

/**
 * Lock a persistence object
 * @author rgrey
 */
class LockItem {

    private static final List locks = []
    private static final pattern = /<([a-z,1-9,-]*).*id="([0-9]*)".*>/

    /**
     * Retrieve lock if no unique keys are currently locked
     * @param obj
     * @param keys
     * @param serial
     * @param p priority in milliseconds
     * @param alive how many attempts before considered deadlock
     * @return refreshed object
     */
    public static Object lock(Object obj, List keys, XmlSerializer serial, 
            int p=1500, int alive=10) {
        try {
            if (! obj || ! obj.id) return obj
            while (! getlock(keys)) {
                if (! alive) throw new DeadLockException()
                Thread.sleep(p)
                alive--
            }
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
     * @param keys
     * @param serial
     */
    public static unlock(List keys, XmlSerializer serial) {
        try {
            serial.endTransaction();
            serial.closeSession();
        } finally {
            Log.debug "LockItem.unlock() : ${locks.size()} current"
            if (! keys) return
            String tname = Thread.currentThread().name
            Log.debug "LockItem.unlock() : unlocked on ${tname}"
            locks.removeAll(keys)
            Log.debug "LockItem.unlock() : ${locks.size()} remain"
        }
    }

    /**
     * Generate a list of key/types for this object (i.e. "survey1234","page4567")
     * @param str
     * @return
     */
    public static List keys(String str) {
        def r = str =~ pattern
        def result = []
        while (r.find()) { result.add(genkey(r.group(2), r.group(1))) }
        return result
    }

    /**
     * Convenience
     * @param obj
     * @param serial
     * @return
     */
    public static List keys(Object obj, XmlSerializer serial) {
        return keys(serial.serialize(obj))
    }

    /**
     * Generate the locking key name
     * @param key the id of the object
     * @param _class the class of the object
     * @return
     */
    public static String genkey(String key, String _class) {
        return PersistUtils.lowerCamelCase(_class) + key
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

/**
 * Deadlock persistence lock
 * @author rgrey
 */
public class DeadLockException extends RuntimeException {

    public DeadLockException() {
        super("Persistence deadlock detected!!!")
    }
    
}
