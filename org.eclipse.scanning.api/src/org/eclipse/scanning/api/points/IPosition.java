package org.eclipse.scanning.api.points;

import java.util.List;

/**
 * 
 * A position is a location of one or more values.
 * For instance a group of scannables which need to be at a
 * certain location. For exmaple at the start of a scan or
 * for a scan datapoint.
 * 
 * The position might be X and Y or Temperature or all three.
 * 
 * @author Matthew Gerring
 *
 */
public interface IPosition {
	
	// TODO FIXME The position must also have the indices of the data as well as the
	// actual scannable value. This is required for NeXusw writing.
	
	/**
	 * The number of named scalars in this position
	 * @return
	 */
	int size();

	/**
	 * The names of the scalars set for this position.
	 * For instance 'x' and 'y' for a map or 'Temperature'
	 * 
	 * @return
	 */
	List<String> getNames();
	
	/**
	 * The value of a named position. For instance get("X") to return the value of the 
	 * X IScannable double.
	 * 
	 * @param name
	 * @return
	 */
	Object get(String name);
	
	// TODO 
	// int getIndex(name); 
	
	/**
	 * Creates a composite position with the values of this position 
	 * and the values of the passed in position.
	 * 
	 * This position's values take precedence if the names conflict.
	 * This is like an implementation of Map where the putAll does not
	 * overwrite.
	 * 
	 * @param other
	 * @return
	 */
	IPosition composite(IPosition other);
	
	/**
	 * The step where the position was in a scan, if it is a position being
	 * generated from a scan. If not the value will be -1
	 * @return
	 */
	int getStepIndex();
	
	/**
	 * The step where the position was in a scan, if it is a position being
	 * generated from a scan. If not the value will be -1
	 * @param step
	 */
	void setStepIndex(int step);
}
