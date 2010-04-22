package org.chi.persistence;

import groovy.lang.ExpandoMetaClassCreationHandle;
import groovy.lang.GroovySystem;
import groovy.lang.MetaClassRegistry;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.Collection;
import java.util.HashMap;

import org.chi.util.FileUtil;
import org.chi.util.GeneralException;
import org.chi.util.IOUtils;
import org.chi.util.Log;
import org.chi.util.StringUtil;
import org.codehaus.groovy.grails.commons.DefaultGrailsApplication;
import org.codehaus.groovy.grails.commons.GrailsApplication;
import org.codehaus.groovy.grails.compiler.injection.ClassInjector;
import org.codehaus.groovy.grails.compiler.injection.GrailsAwareClassLoader;
import org.codehaus.groovy.grails.orm.hibernate.cfg.DefaultGrailsDomainConfiguration;
import org.hibernate.SessionFactory;
import org.hibernate.tool.hbm2ddl.SchemaUpdate;

/**
 * Dynamically add domain grails domain classes
 * @author rgrey
 */
public class PersistenceLoader extends HibernateTools {
    
    private static final String TEMPLATE_FILE = "domainclass.groovy";
    private static final HashMap<String, PersistenceLoader> inst; 
    private HashMap<String, Class<?>> classes = new HashMap<String, Class<?>>();
    
    static {
        inst = new HashMap<String, PersistenceLoader>(); 
    }
    
    private GrailsAwareClassLoader cl;
    private SessionFactory sessionFactory;
    private String template;
    private String key;

    /**
     * Create the class loader 
     */
    private PersistenceLoader(String key) {
        cl = new GrailsAwareClassLoader(
            Thread.currentThread().getContextClassLoader());
        cl.setClassInjectors(new ClassInjector[] {new DomainObjectLoader()});
        this.key = key;
    }
    
    /**
     * Singleton
     * @param key the configuration file
     * @return
     */
    public static PersistenceLoader getInstance(String key) {
        if (inst.get(key) == null) inst.put(key, new PersistenceLoader(key));
        return inst.get(key);
    }
    
    @Override
    public SessionFactory getSF() { return sessionFactory; }
    
    /**
     * Get the hibernate loaded classes
     * @return
     */
    public Collection<Class<?>> getClasses() {
        return classes.values();
    }

    /**
     * Add the precursor definition class
     * @param name
     * @return the new class
     */
    public Class<?> addBaseClass(DomainClass dc) throws GeneralException {
        if (sessionFactory != null) 
            throw new GeneralException("Can not add class after load is called");
        if (classes.get(dc.getName()) != null) return classes.get(dc.getName());
        Class<?> _class = cl.parseClass(loadDomainTemplate(dc));
        classes.put(dc.getName(), _class);
        Log.debug("PersistenceLoader.addBaseClass() : load " + _class.getName());
        return _class;
    }
    
    /**
     * Load the session factory
     * @param updateDb should we also update the existing database? 
     */
    public void load(boolean updateDb) throws GeneralException {
        if (classes.size() == 0) return;
        if (sessionFactory != null) 
            throw new GeneralException("Load has already been called");
        MetaClassRegistry registry = GroovySystem.getMetaClassRegistry();
        registry.setMetaClassCreationHandle(new ExpandoMetaClassCreationHandle());
        GrailsApplication application = new DefaultGrailsApplication(
                classes.values().toArray(new Class[0]), cl);
        application.initialise();
        DefaultGrailsDomainConfiguration ac = new DefaultGrailsDomainConfiguration();
        ac.setGrailsApplication(application);
        URL cfg = IOUtils.classLoadURL(key);
        if (cfg == null) 
            throw new GeneralException("Missing config [" + key + "]");
        ac.configure(cfg);
        if (updateDb) {
            SchemaUpdate update = new SchemaUpdate(ac);
            update.execute(true, true);
        }
        sessionFactory = ac.buildSessionFactory();
        Log.debug("PersistenceLoader.load() : loaded " + 
                sessionFactory.getAllClassMetadata().size() + " class(es)");
    }
    
    /**
     * Convenience
     * @return
     */
    public boolean isLoaded() { return getSF() != null; }
    
    /**
     * Load the domain template for this new class
     * @param dc
     * @throws GeneralException
     */
    private String loadDomainTemplate(DomainClass dc) throws GeneralException {
        if (! StringUtil.isEmpty(template)) return dc.buildTemplate(template);
        try {
            InputStream tFile = IOUtils.classLoadFile(TEMPLATE_FILE);
            if (tFile == null) throw new GeneralException(
                    "Unable to find [" + TEMPLATE_FILE + "]");
            template = FileUtil.readFile(new InputStreamReader(tFile));
            return dc.buildTemplate(template);
        } catch (IOException e) {
            throw new GeneralException(e);
        }
        
    }
    
}
