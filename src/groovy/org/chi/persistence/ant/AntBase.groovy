package org.chi.persistence.ant

import org.apache.tools.ant.Task
import org.chi.persistence.RelaxSchema
import org.chi.persistence.SchemaLoader

/**
 * Shared ant funtionality
 * @author rgrey
 */
abstract class AntBase extends Task {

    /**
     * Load a schema loader
     * @param hibCfg
     * @param schemaDir
     * @return
     */
    SchemaLoader schemaLoader(hibCfg, schemaDir) {
        return new SchemaLoader(hibCfg, schemaDir, RelaxSchema.class)
    }

}

