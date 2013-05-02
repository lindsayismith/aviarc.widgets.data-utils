package nz.co.aviarc.featuremanager.widgets;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import com.aviarc.core.dataset.Dataset;
import com.aviarc.framework.toronto.screen.ScreenRenderingContext;

/**
 * The DefaultClientDataContextNodeImpl stores a map of Datasets that are
 * represented client-side by an equivalent data context node.
 *
 * It has two flags to determine the behaviour of this node in the tree.
 *
 * The treatAsRoot flag, if true, will mean that this node does not expose
 * any of the datasets from its parent,  and will return null from getParent.
 *
 * The addDatasetsLocally flag determines whether this node will add Datasets
 * from its own State when addClientSideDataset is called.  If true, it will,
 * if false, it will defer to its parent.
 *
 * If the node is a root node, then it must add datasets locally.
 *
 * By default, a node is not a parent, and does not add datasets locally, so
 * it is a pass-through node with potentially some shadowing datasets.
 *
 *
 *
 *
 * @author lindsay
 *
 */
public class DataContextClientDataContextNodeImpl extends com.aviarc.framework.toronto.datacontext.DefaultClientDataContextNodeImpl {

    private Set<String> _clientSideDatasetNames;
    private Map<String, Dataset> _localDatasets;

    public DataContextClientDataContextNodeImpl(ScreenRenderingContext renderingContext,
                                                String id,
                                                Map<String, Dataset> localDatasets,
                                                Set<String> clientSideDatasetNames,
                                                boolean treatAsRoot,
                                                boolean addDatasetsLocally) {
        super(renderingContext, id, localDatasets, treatAsRoot, addDatasetsLocally);
        _clientSideDatasetNames = clientSideDatasetNames;
        _localDatasets = localDatasets;
    }

    // LIS - should this be all our datasets?  Or just the ones on the client?
    // SHould be ones on the client as it is used for exporting, and for application
    // of rules from the client
    public Map<String, Dataset> getLocalClientSideDatasets() {
        HashMap<String, Dataset> result = new HashMap<String, Dataset>();
        for (String clientSideName : this._clientSideDatasetNames) {
            result.put(clientSideName, _localDatasets.get(clientSideName));
        }
        
        return result;
    }
    
}