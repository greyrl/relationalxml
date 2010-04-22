package org.chi.persistence.util

import org.chi.persistence.PersistUtils
import org.chi.persistence.RelaxSchema
import org.chi.persistence.XmlSerializer
import org.chi.persistence.util.LockItem
import org.chi.util.Log
import org.chi.util.PooledThread

/**
 * Update the database cache
 * @author rgrey
 */
class UpdateCache {

    private static final String C = RelaxSchema.cName
    private static HashMap tempCache = []

    private Object update
    private XmlSerializer serializer

    /**
     * Run the update in a thread or synchroniously
     * @param obj
     * @param serial
     * @param over override object for serialization
     */
    public static update(obj, XmlSerializer serial, over=null) {
        String msg = "update [${obj.class}], id [${obj.id}]"
        Log.debug "UpdateCache.update() : ${msg}"
        tempCache.put(obj.hashCode(), serial.serialize(obj, null, over))
        def cache = serial.getDomainClass(C).newInstance()
        cache.oid = obj.id
        cache.otype = obj.class.name
        serial.session.save(cache)
        UpdateThread.start(serial)
        Log.debug "UpdateCache.update() : finish"
    }

    /**
     * Retrieve a memory or database cache value
     * @param obj
     * @return
     */
    public static get(obj) {
        if (! obj) return null
        if (obj.metaClass.hasProperty(obj, "id") && ! obj.id) return null
        def val = tempCache.get(obj.hashCode())
        boolean hascache = obj.metaClass.hasProperty(obj, "xmlcache")
        return val ? val : hascache ? obj.xmlcache.trim(): null
    }

    /**
     * Remove an item from short term cache
     * @param obj
     */
    public static remove(obj) {
        Log.debug "UpdateCache.remove() : size before ${tempCache.size()}"
        tempCache.remove(obj.hashCode())
        Log.debug "UpdateCache.remove() : size after ${tempCache.size()}"
    }

}

/**
 * Threaded cache update
 * @author rgrey
 */
class UpdateThread extends PooledThread {

    private static final int PR = Thread.MIN_PRIORITY
    // in seconds...
    private static final int TIMER = 600
    private static final HashMap running = []

    private XmlSerializer serial
    private boolean run = true

    /**
     * Start the thread
     * @param serial
     */
    public static start(XmlSerializer serial) {
        if (running.get(serial)) return
        def instance = new UpdateThread(serial)
        instance.queue(PR)
        running.put(serial, instance)
    }

    /**
     * Stop all instances
     */
    public static stopAll() {
        Log.debug "UpdateThread.stopAll() : stop ${running.size()}"
        running.each { key, val -> val.run = false }
        running.clear()
    }

    /**
     * Singleton constructor
     * @param serial
     */
    private UpdateThread(XmlSerializer serial) {
        this.serial = serial
    }

    @Override
    void run() {
        while (run) {
            this.update()
            Thread.sleep(TIMER * 1000)
        }
    }

    /**
     * Grab all the items from the cache table and apply updates
     */
    private update() {
        // TODO add tests
        String msg = "UpdateThread.update() : "
        Log.debug msg + "begin ${serial.hashCode()}"
        try {
            Set complete = []
            int count = 0
            def items = serial.session.createQuery("from Cache").list()
            items.each {
                def i = it.oid
                Class t = serial.getDomainClass(it.otype)
                if (! t) {
                    Log.error("Unknown persistence class [${it.otype}]")
                    return
                }
                i = it.oid.asType(t.metaClass.getMetaProperty("id").type)
                Log.debug msg + "update ${i}, type ${t}"
                def update = serial.session.get(t, i)
                def done = ! update || complete.contains(update.hashCode())
                def keys = null
                try {
                    if (! done) { 
                        keys = LockItem.keys(update, serial)
                        update = LockItem.lock(update, keys, serial)
                    } else {
                        keys = LockItem.keys(it, serial)
                        it = LockItem.lock(it, keys, serial)
                    }
                    if (! done) update.xmlcache = serial.serialize(update)
                    if (! done) serial.session.save(update)
                    serial.session.delete(it)
                    if (! done) UpdateCache.remove(update)
                    if (! done) complete.add(update.hashCode())
                    serial.succeed()
                } finally {
                    LockItem.unlock(keys, serial)
                }
                Log.debug msg + "total ${items.size()}, complete ${++count}"
            }
        } catch (Exception e) {
            Log.exception("Unable to update cache", e)
        }
        Log.debug "UpdateThread.update() : finish"
    }

}
