package org.chi.persistence;

import groovy.lang.GroovyObjectSupport;

import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;

/**
 * Separated out to make persistence loader a little cleaner
 * @author rgrey
 */
public abstract class HibernateTools extends GroovyObjectSupport {
    
    private final ThreadLocal<Session> session = new ThreadLocal<Session>();
    private final ThreadLocal<Transaction> tx = new ThreadLocal<Transaction>();
    private final ThreadLocal<Boolean> success = new ThreadLocal<Boolean>(); 
    
    /**
     * Retrieve the new session factory
     * @return session factory
     */
    public abstract SessionFactory getSF();
    
    /**
     * Get an instance of the session factory
     * @return
     */
    public Session getSession() {
        if (session.get() == null) {
            session.set(getSF().openSession());
        }
        return session.get();
    }
    
    /**
     * Close the hibernate session
     */
    public void closeSession() {
        if (session.get() == null) return;
        if (session.get().isOpen()) session.get().close();
        session.set(null);
    }
    
    /**
     * Start an internal transaction
     */
    public void beginTransaction() {
        if (tx.get() != null) return;
        success.set(false);
        tx.set(getSession().beginTransaction());
    }
    
    /**
     * Set the operations as successful. Will suggest if we should commit later
     */
    public void succeed() { success.set(true); }
    
    /**
     * End a transaction
     */
    public boolean endTransaction() {
        if (tx.get() == null) return success.get();
        try {
            if (success.get()) tx.get().commit(); 
            else tx.get().rollback();
        } finally {
            tx.set(null);
        }
        return success.get();
    }

}
