package drawingboard.widgets;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.aviarc.core.dataset.Dataset;
import com.aviarc.core.dataset.unbound.UnboundDataset;
import com.aviarc.core.dataset.unbound.UnboundDataset.CreateRowContextImpl;
import com.aviarc.core.dataset.unbound.UnboundDataset.SetCurrentRowIndexContextImpl;
import com.aviarc.core.dataset.unbound.UnboundDatasetRow;
import com.aviarc.core.dataset.unbound.UnboundDatasetRow.SetFieldContextImpl;
import com.aviarc.core.datatype.AviarcBoolean;
import com.aviarc.core.state.State;
import com.aviarc.core.util.RandomID;
import com.aviarc.framework.dataset.unbound.UnboundDatasetImpl;
import com.aviarc.framework.toronto.datacontext.DatasetShadowingDataContextNode;
import com.aviarc.framework.toronto.datacontext.DatasetShadowingRenderingContext;
import com.aviarc.framework.toronto.datacontext.DatasetShadowingState;
import com.aviarc.framework.toronto.screen.CompiledWidget;
import com.aviarc.framework.toronto.screen.RenderedNode;
import com.aviarc.framework.toronto.screen.ScreenRenderingContext;
import com.aviarc.framework.toronto.widget.DefaultDefinitionFile;
import com.aviarc.framework.toronto.widget.DefaultRenderedNodeFactory;
import com.aviarc.framework.xml.compilation.ResolvedElementContext;

public class StaticData implements DefaultRenderedNodeFactory {
	private static final long serialVersionUID = 1L;
 
 
    /*
     * (non-Javadoc)
     * @see com.aviarc.framework.toronto.widget.RenderedNodeFactory#createRenderedNode(com.aviarc.framework.xml.compilation.ResolvedElementContext, com.aviarc.framework.toronto.screen.ScreenRenderingContext)
     */
    public RenderedNode createRenderedNode(ResolvedElementContext<CompiledWidget> elementContext,
    		ScreenRenderingContext renderingContext) {

    	DatasetShadowingState innerState = new DatasetShadowingState(renderingContext.getCurrentState());


    	List<ResolvedElementContext<CompiledWidget>> dataElements = elementContext.getSubElements("data");
    	// There will be only one, if any
    	if (dataElements.size() > 0) {
    		ResolvedElementContext<CompiledWidget> dataElement = dataElements.get(0);

    		List<UnboundDataset> staticDatasets = createStaticDatasets(dataElement, innerState);   

    		for (UnboundDataset uds : staticDatasets) {
    			Dataset ds = uds.makeDataset(innerState);
    			innerState.addShadowingDataset(ds);
    		}            
    	}                

    	DatasetShadowingRenderingContext innerContext = new DatasetShadowingRenderingContext(innerState, 
    			renderingContext.getNameManager(), 
    			renderingContext.getNextKID(), 
    			renderingContext.isEmbeddedScreen(),
    			renderingContext.getContainingScreenName());

    	DatasetShadowingDataContextNode dataNode = new DatasetShadowingDataContextNode(innerContext, RandomID.getShortRandomID());

    	dataNode.addChildRenderedNodes(elementContext, innerContext);

    	return dataNode;
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
}
