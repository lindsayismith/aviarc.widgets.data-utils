package nz.co.aviarc.featuremanager.widgets;

import java.util.HashMap;
import java.util.Map;

import com.aviarc.core.application.ApplicationInstance;
import com.aviarc.core.dataset.Dataset;
import com.aviarc.core.dataset.DatasetRow;
import com.aviarc.core.exceptions.CannotCreateWorkflowException;
import com.aviarc.core.exceptions.CommandException;
import com.aviarc.core.execution.StackExecutor;
import com.aviarc.core.execution.TerminationReason;
import com.aviarc.core.execution.WorkflowEntryPoint;
import com.aviarc.core.state.State;
import com.aviarc.core.util.RandomID;

import com.aviarc.framework.toronto.datacontext.isolating.DatasetIsolatingStateImpl;
import com.aviarc.framework.toronto.screen.CompiledWidget;
import com.aviarc.framework.toronto.screen.RenderedNode;
import com.aviarc.framework.toronto.screen.ScreenRenderingContext;
import com.aviarc.framework.toronto.screen.ScreenRenderingContextImpl;
import com.aviarc.framework.toronto.widget.DefaultDefinitionFile;
import com.aviarc.framework.toronto.widget.DefaultRenderedNodeFactory;
import com.aviarc.framework.xml.compilation.ResolvedElementAttribute;
import com.aviarc.framework.xml.compilation.ResolvedElementContext;

public class DataContextNodeFactory implements DefaultRenderedNodeFactory {
    private static final long serialVersionUID = 0L;

    public RenderedNode createRenderedNode(ResolvedElementContext<CompiledWidget> elementContext,
                                           ScreenRenderingContext renderingContext) {
        State currentState = renderingContext.getCurrentState();
        ApplicationInstance currentInstance = currentState.getCurrentApplicationInstance();

        Map<String, String> parameters = new HashMap<String, String>();
        // Read in parameters
        for (ResolvedElementContext<CompiledWidget> parameterBlock : elementContext.getSubElements("parameters")) {
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
        
        
        // Create parameters dataset
        Dataset dsParameters = wrappedDataContextState.getApplicationState().getDatasetStack().createDataset("parameters", null);
        DatasetRow row = dsParameters.createRow();
        for (Map.Entry<String, String> entry : parameters.entrySet()) {
            row.setField(entry.getKey(), entry.getValue());
        }
        
        
        
        ResolvedElementAttribute attr = elementContext.getAttribute("workflow");
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
