package org.chi.persistence

import groovy.util.slurpersupport.GPathResult

import org.chi.util.Log

/**
 * Load database objects from a schema
 * @author rgrey
 */
class SchemaLoader {

    private directory
    private loader
    private schemaClass

    /**
     * Default
     * @param hibernateCfg the name of the hibernate configuraton file to class load
     * @param directory the directory where the schema exist
     * @param schema the implementation of {@link SchemaParser} used to generate classes
     * @return
     */
    def SchemaLoader(String hibernateCfg, def directory, Class schema) {
        if (directory in String) this.directory = new File(directory)
        else this.directory = directory
        Log.debug "SchemaLoader() : directory [${directory}]"
        loader = PersistenceLoader.getInstance(hibernateCfg);
        this.schemaClass = schema
    }

    /**
     * Load the schema classes and start hibernate
     * @param queryFile to start the {@link XmlSerializer} with
     * @param buildDb build missing schema
     * @return
     */
    public XmlSerializer load(String queryFile, boolean buildDb) {
        assert schemaClass in SchemaParser
        schemaClass.newInstance().getClasses(directory).each { 
            Log.console "SchemaLoader.load() : load class [${it.name}]"
            loader.addBaseClass(it)
        }
        loader.load(buildDb)
        return new XmlSerializer(loader.getSF(), queryFile, schemaClass)
    }

}

/**
 * Interface that should be implemented by any schema classes that 
 * generate classes
 * @author rgrey
 */
interface SchemaParser {

    /**
     * Get the classes associated from the schemas in a particular directory.
     * Should be in order they need to be loaded (i.e. dependencies first)
     * @param directory
     * @return
     */
    DomainClass[] getClasses(File directory)   

    /**
     * Validate a document and produce a {@link GPathResult}
     * @param reader the XML reader
     * @param schema the schema as a string
     * @param externals map of external refs for the resolver
     * @return
     */
    GPathResult validate(Reader reader, String schema, Map externals);

}
