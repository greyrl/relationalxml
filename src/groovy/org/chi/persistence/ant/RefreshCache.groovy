package org.chi.persistence.ant

import org.apache.tools.ant.BuildException
import org.chi.util.Log
import org.chi.persistence.util.UpdateCache

/**
 * Refresh the cache for an object class
 * @author rgrey
 */
class RefreshCache extends AntBase {

    def String hibernateConfig
    def String schemaDir
    def String queryFile
    def String updateQuery

    @Override
    public void execute() throws BuildException {
        def sl = schemaLoader(hibernateConfig, schemaDir)
        def xsls = sl.load(queryFile, true)
        Log.console("RefreshCache() : update with query [${updateQuery}]")
        try {
            // TODO need to manually update parents now...
            xsls.session.createQuery(updateQuery).list().each() {
                Log.console("RefreshCache() : update [${it.id}]")
                UpdateCache.update(it, xsls)
            }
        } finally {
            xsls.closeSession()
        }
        Log.console("RefreshCache() : done")
    }

}
