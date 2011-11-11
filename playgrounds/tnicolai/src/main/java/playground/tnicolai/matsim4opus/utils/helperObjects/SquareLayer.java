package playground.tnicolai.matsim4opus.utils.helperObjects;

import java.util.ArrayList;
import java.util.Map;

import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Node;

import com.vividsolutions.jts.geom.Polygon;

public class SquareLayer {

	/** field needed for google maps output ... */
	private Polygon square = null;
	
	/** fields regarding square centroid (Layer1) */
	private Id squareCentroidNodeID = null;
	private double squareCentroidAccessibility = 0.;
	private Coord squareCentroidCoord = null;
	
	/** fields regarding square mean value (Layer2) */
	private ArrayList<Id> squareMeanNodeList = null;
	private double squareMeanAccessibility = 0.;
	
	/** fields regarding derivation between Layer 1 and 2 (Layer3) */
	private double squareAccessibilityDerivation = 0.;
	
	public void setSquareCentroid(Id nearestNodeID, Polygon polygon, Coord squareCentroid){
		this.squareCentroidNodeID = nearestNodeID;
		this.square = polygon;
		this.squareCentroidCoord = squareCentroid;
	}
	
	public void addNode(Node node){
		if(this.squareMeanNodeList == null)
			this.squareMeanNodeList = new ArrayList<Id>();
		this.squareMeanNodeList.add( node.getId() );
	}
	
	public void computeDerivation(final Map<Id, Double> resultMap){
		
		assert(resultMap != null);
		
		// Step 1: get accessibility value for square centroid
		if(this.squareCentroidNodeID == null)
			return;
		this.squareCentroidAccessibility = resultMap.get( this.squareCentroidNodeID );
		
		// Step 2: get accessibility values for remaining nodes within this square (if available)
		if(this.squareMeanNodeList != null){
			
			// Step 2.1: sum over all accessibility values
			int numberOfNodes = this.squareMeanNodeList.size();
			for(int index = 0; index < numberOfNodes; index++){
				Id nodeId = this.squareMeanNodeList.get( index );
				this.squareMeanAccessibility =+ resultMap.get( nodeId );
			}
			// Step 2.2: get mean accessibility value
			this.squareMeanAccessibility = (this.squareMeanAccessibility / numberOfNodes);
			
			// Step 3: determine accessibility derivation
			this.squareAccessibilityDerivation = Math.abs( this.squareMeanAccessibility - this.squareCentroidAccessibility );
		}
		else{
			this.squareMeanAccessibility = this.squareCentroidAccessibility;
		}
	}
	
	public Polygon getPolygon(){
		return this.square;
	}
	public Coord getSquareCentroidCoord(){
		return this.squareCentroidCoord;
	}
	public double getCentroidAccessibility(){
		return this.squareCentroidAccessibility;
	}
	public double getMeanAccessibility(){
		return this.squareMeanAccessibility;
	}
	public double getAccessibilityDerivation(){
		return this.squareAccessibilityDerivation;
	}
}