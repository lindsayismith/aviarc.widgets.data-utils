package com.aviarc.widgets.datacontext;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;

import org.w3c.dom.Element;
import org.w3c.dom.Node;

import com.aviarc.core.InterfaceQuery;
import com.aviarc.core.UnsupportedInterfaceException;
import com.aviarc.core.dataset.Dataset;
import com.aviarc.core.logging.AviarcLogger;
import com.aviarc.core.logging.LoggingHub;
import com.aviarc.framework.toronto.datacontext.AbstractDataContextNodeImpl;
import com.aviarc.framework.toronto.datacontext.ClientDataContextNode;

import com.aviarc.framework.toronto.screen.RenderedNode.XHTMLCreationContext;
import com.aviarc.framework.toronto.screen.ScreenRenderingContext;
import com.aviarc.framework.toronto.screen.TorontoClientSideCapable;
import com.aviarc.framework.toronto.screen.TorontoClientSideCapableCreator;
import com.aviarc.framework.toronto.screen.postback.DatasetDependencyList;

/**
 * Isolates datasets in a context based on what datasets are available in the parent state.
 *
 * When the parent data context asks for the datasets that it should provide, return back
 * all of the datasets that are required by my child widgets that are available in the parent state.
 *
 * When creating a JS representation, send datasets that aren't available in my parent state.
 *
 */
public class DatasetIsolatingDataContextNodeImpl extends AbstractDataContextNodeImpl {
    

    private static final long serialVersionUID = 0L;

    private Set<String> _upstreamRequiredDatasets;

    private Set<String> _clientSideDatasetNames;

    private static final AviarcLogger logger = LoggingHub.getGeneralLogger();

    public DatasetIsolatingDataContextNodeImpl(final ScreenRenderingContext renderingContext, final String id) {
        super(renderingContext, id);
    }
    
    private void setClientSideDatasetNames(Set<String> keySet) {
        _clientSideDatasetNames = keySet;
    }
    
    private Set<String> getClientSideDatasetNames() {
        return _clientSideDatasetNames;
    }


    public ClientDataContextNode createClientSideNode() {
        return new DataContextClientDataContextNodeImpl(getRenderingContext(), getID(), getLocalDatasets(), getClientSideDatasetNames(), false, true);
    }

    public Set<String> getDatasetsParentShouldProvide() {
        return _upstreamRequiredDatasets;
    }

    public String getJavascriptDeclaration() {
        return "new Toronto.framework.DefaultDataContextImpl(\"" + getID() + "\", [" + makeDatasetsJavascript() + "])";
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();

        builder.append("DI_DC: " + getID());
        builder.append(" [");

        Set<String> datasets = getLocalDatasets().keySet();
        boolean isFirst = true;
        for (String dsName : datasets) {
            if (!isFirst) {
                builder.append(", ");
            } else {
                isFirst = false;
            }

            builder.append(dsName);
        }
        builder.append("]");

        return builder.toString();
    }

    public Map<String, Dataset> getLocalDatasets() {
        Map<String, Dataset> datasets = new HashMap<String, Dataset>();
        for (Dataset dataset : getRenderingContext().getCurrentState().getApplicationState().getDatasetStack().allDatasets()) {
            datasets.put(dataset.getName(), dataset);
        }
        return datasets;
    }

    // LIS Modified from framework version - we don't assume that all datasets are going to the client
    public void resolveDatasets() {        

        // All datasets required for the client by things below us
        Set<String> downstreamRequiredDatasetNames = this.collectDownstreamRequiredDatasetNames();

        // Ones required from our parent
        Set<String> upstreamRequiredDatasetNames = new HashSet<String>();
        
        // All the datasets we have locally
        Map<String, Dataset> localDatasets = this.getLocalDatasets();        

        DatasetDependencyList ddl = new DatasetDependencyList();
        
        // The ones that are exported
        HashMap<String, TorontoClientSideCapable> clientSideDatasets = new HashMap<String, TorontoClientSideCapable>();

        // All required names - will be augmented by transitive requirements as they are encountered 
        LinkedList<String> requiredDatasetNames = new LinkedList<String>(downstreamRequiredDatasetNames);
        
        while (requiredDatasetNames.size() > 0) {
            String datasetName = requiredDatasetNames.poll();
            
            // If we have it locally, then that one is to be sent to the client
            if (localDatasets.containsKey(datasetName)) {
                Dataset clientDataset = localDatasets.get(datasetName);
                
                checkDatasetPermissions(clientDataset);
                
                try {
                    TorontoClientSideCapableCreator tcscc = InterfaceQuery.queryInterface(clientDataset, TorontoClientSideCapableCreator.class);
                    TorontoClientSideCapable tcsc = tcscc.makeClientSideCapable(getRenderingContext());

                    // datasets required by these ones are added to our list
                    Set<String> requiredDatasets = tcsc.getRequiredDatasets();
                    if (requiredDatasets != null) {
                        requiredDatasetNames.addAll(requiredDatasets);                        
                    }

                    ddl.addDataset(clientDataset);
                    clientSideDatasets.put(clientDataset.getName(), tcsc);
                } catch (UnsupportedInterfaceException e) {
                    // Just log the error for now
                    logger.error("DatasetIsolatingDataContextNodeImpl: Cannot resolve Dataset '" + clientDataset.getName() +":" + clientDataset.getID() +
                                "' as Dataset type is not supported: " + clientDataset.getClass().toString());
                    continue;
                }
            } else {
                // Otherwise it will be required by our parents
                upstreamRequiredDatasetNames.add(datasetName);
            }
        }
        
        
        ddl.sort();

        this.setClientSideDatasets(clientSideDatasets);
        
        // We also need to remember the ones that are sent to the client
        this.setClientSideDatasetNames(new HashSet<String>(clientSideDatasets.keySet()));

        _upstreamRequiredDatasets = upstreamRequiredDatasetNames;
    }

  

}
