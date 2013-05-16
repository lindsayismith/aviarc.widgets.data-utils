package com.aviarc.widgets.datacontext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.w3c.dom.Element;
import org.w3c.dom.Node;

import com.aviarc.core.application.ApplicationInstance;
import com.aviarc.core.dataset.Dataset;
import com.aviarc.core.dataset.DatasetRow;
import com.aviarc.core.dataset.unbound.UnboundDataset;
import com.aviarc.core.dataset.unbound.UnboundDatasetRow;
import com.aviarc.core.dataset.unbound.UnboundDataset.CreateRowContextImpl;
import com.aviarc.core.dataset.unbound.UnboundDataset.SetCurrentRowIndexContextImpl;
import com.aviarc.core.dataset.unbound.UnboundDatasetRow.SetFieldContextImpl;
import com.aviarc.core.datatype.AviarcBoolean;
import com.aviarc.core.exceptions.CannotCreateWorkflowException;
import com.aviarc.core.exceptions.CommandException;
import com.aviarc.core.execution.StackExecutor;
import com.aviarc.core.execution.TerminationReason;
import com.aviarc.core.execution.WorkflowEntryPoint;
import com.aviarc.core.state.State;
import com.aviarc.core.util.RandomID;

import com.aviarc.framework.dataset.unbound.UnboundDatasetImpl;
import com.aviarc.framework.toronto.datacontext.isolating.DatasetIsolatingStateImpl;
import com.aviarc.framework.toronto.screen.CompiledWidget;
import com.aviarc.framework.toronto.screen.RenderedNode;
import com.aviarc.framework.toronto.screen.ScreenRenderingContext;
import com.aviarc.framework.toronto.screen.ScreenRenderingContextImpl;
import com.aviarc.framework.toronto.screen.RenderedNode.XHTMLCreationContext;
import com.aviarc.framework.toronto.screen.ScreenRequirementsCollector;
import com.aviarc.framework.toronto.widget.DefaultDefinitionFile;
import com.aviarc.framework.toronto.widget.DefaultRenderedNodeFactory;
import com.aviarc.framework.toronto.widget.DefaultRenderedWidgetImpl;
import com.aviarc.framework.xml.compilation.ResolvedElementAttribute;
import com.aviarc.framework.xml.compilation.ResolvedElementContext;

public class DataContextNodeFactory implements DefaultRenderedNodeFactory {
    private static final String PARAMETERS_DATASET_NAME = "parameters";
    private static final String STATiC_DATA_SUBELEMENT = "static-datasets";
    private static final String WORKFLOW_PARAMETERS_SUBELEMENT = "workflow-parameters";
    private static final String DATA_CONTEXT_WORKFLOW_ATTR = "workflow";
    private static final long serialVersionUID = 0L;

    public RenderedNode createRenderedNode(ResolvedElementContext<CompiledWidget> elementContext,
                                           ScreenRenderingContext renderingContext) {
        State currentState = renderingContext.getCurrentState();
        ApplicationInstance currentInstance = currentState.getCurrentApplicationInstance();

        Map<String, String> parameters = new HashMap<String, String>();
        // Read in parameters
        for (ResolvedElementContext<CompiledWidget> parameterBlock : elementContext.getSubElements(WORKFLOW_PARAMETERS_SUBELEMENT)) {
            for (ResolvedElementContext<CompiledWidget> param : parameterBlock.getSubElements("param")) {
                parameters.put(param.getAttribute("name").getResolvedValue(), param.getAttribute("value").getResolvedValue());
            }
                
        }
        
        
        // Create a new state to ensure that the execution context is new, but use old
        // app state so we have access to all the old datasets within this datacontext too
        State dataContextState = currentInstance.getEntryPointStateFactory()
                .createApplicationEntryPointState(currentState.getRequestState(), currentState.getApplicationState());        
      
        
        
        DatasetIsolatingStateImpl wrappedDataContextState = new DatasetIsolatingStateImpl(dataContextState);
        
        
        wrappedDataContextState.getApplicationState().getDatasetStack().startDatasetScopingBlock();
        
        // Add authentication dataset
        Dataset dsAuth = currentState.getApplicationState().getDatasetStack().findDataset("authentication");
        wrappedDataContextState.getApplicationState().getDatasetStack().addDataset(dsAuth.getUnboundDataset().makeDataset(wrappedDataContextState));
        
        
        
        // Add in the datasets from the 'data' subelement
        List<ResolvedElementContext<CompiledWidget>> dataElements = elementContext.getSubElements(STATiC_DATA_SUBELEMENT);
        // There will be only one, if any
        if (dataElements.size() > 0) {
            ResolvedElementContext<CompiledWidget> dataElement = dataElements.get(0);

            List<UnboundDataset> staticDatasets = createStaticDatasets(dataElement, wrappedDataContextState);   

            for (UnboundDataset uds : staticDatasets) {
                Dataset ds = uds.makeDataset(wrappedDataContextState);
                wrappedDataContextState.getApplicationState().getDatasetStack().addDataset(ds);
            }            
        }     
        
        // Create parameters dataset
        Dataset dsParameters = wrappedDataContextState.getApplicationState().getDatasetStack().createDataset(PARAMETERS_DATASET_NAME, null);
        DatasetRow row = dsParameters.createRow();
        for (Map.Entry<String, String> entry : parameters.entrySet()) {
            row.setField(entry.getKey(), entry.getValue());
        }
        
        
        
        ResolvedElementAttribute attr = elementContext.getAttribute(DATA_CONTEXT_WORKFLOW_ATTR);
        if (attr != null) {
            String workflowName = attr.getResolvedValue();
            runWorkflow(wrappedDataContextState, workflowName);
        }

        ScreenRenderingContext dcRenderingContext = new ScreenRenderingContextImpl(wrappedDataContextState,
                renderingContext.getNameManager(), renderingContext.getNextKID(), renderingContext.isEmbeddedScreen(),
                renderingContext.getContainingScreenName());

        DatasetIsolatingDataContextNodeImpl dcNode = new DatasetIsolatingDataContextNodeImpl(dcRenderingContext,
                RandomID.getShortRandomID());
        dcNode.addChildRenderedNodes(elementContext, dcRenderingContext);

        
        return dcNode;
    }
    
    private List<UnboundDataset> createStaticDatasets(ResolvedElementContext<CompiledWidget> dataElement, final State currentState) {
        // Get all the Datasets elements
        List<ResolvedElementContext<CompiledWidget>> datasetElements = dataElement.getSubElements("dataset");
        if (datasetElements.size() == 0) {
            return Collections.emptyList();
        } 

        ArrayList<UnboundDataset> result = new ArrayList<UnboundDataset>();
        for (ResolvedElementContext<CompiledWidget> datasetElement : datasetElements) {
            result.add(createDataset(datasetElement, currentState));
        }

        return result;
    }
    
    private UnboundDataset createDataset(ResolvedElementContext<CompiledWidget> datasetElement, final State currentState) {
        String datasetName = datasetElement.getAttribute("name").getResolvedValue();

        UnboundDataset result = new UnboundDatasetImpl(datasetName);
        UnboundDatasetRow current = null;

        List<ResolvedElementContext<CompiledWidget>> rowElements = datasetElement.getSubElements("row");

        for (ResolvedElementContext<CompiledWidget> rowElement : rowElements) {
            UnboundDatasetRow row = result.createRow(new CreateRowContextImpl(currentState));
            if (rowElement.hasAttribute("current") && AviarcBoolean.valueOf(rowElement.getAttribute("current").getResolvedValue()).booleanValue()) {
                current = row;
            }

            // Fields
            List<ResolvedElementContext<CompiledWidget>> fieldElements = rowElement.getSubElements("field");
            for (ResolvedElementContext<CompiledWidget> fieldElement : fieldElements) {
                String name = fieldElement.getAttribute("name").getResolvedValue();
                String value = fieldElement.getAttribute("value").getResolvedValue();
                row.setField(new SetFieldContextImpl(currentState, name, value));
            }
        }
        if (current != null) {
            result.setCurrentRowIndex(new SetCurrentRowIndexContextImpl(currentState, current.getDatasetRowIndex()));            
        }


        return result;
    }

    public void initialize(DefaultDefinitionFile definitionFile) {

    }

    private void runWorkflow(State state, String workflowName) {
        // Run the workflow until it terminates
        TerminationReason result;
        try {
            result = StackExecutor.runUntilTerminated(new WorkflowEntryPoint(state, workflowName));
        } catch (CannotCreateWorkflowException ex) {
            throw new CommandException(ex);
        }
        if (!TerminationReason.TIMELINE_STACK_EMPTY.equals(result)) {
            /*
             * The workflow probably wrote to the response, which will probably break whatever invoked this method,
             * but caller should still log this exception
             */
            throw new CommandException("Initial workflow '" + workflowName + "' terminated unexpectedly. TerminationReason: "
                    + result);
        }
    }
    
    

}
