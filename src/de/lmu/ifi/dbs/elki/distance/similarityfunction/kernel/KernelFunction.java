package de.lmu.ifi.dbs.elki.distance.similarityfunction.kernel;

import de.lmu.ifi.dbs.elki.data.DatabaseObject;
import de.lmu.ifi.dbs.elki.distance.distancefunction.PrimitiveDistanceFunction;
import de.lmu.ifi.dbs.elki.distance.distancevalue.Distance;
import de.lmu.ifi.dbs.elki.distance.similarityfunction.PrimitiveSimilarityFunction;

/**
 * Interface Kernel describes the requirements of any kernel function.
 *
 * @author Elke Achtert 
 * @param <O> object type
 * @param <D> distance type
 */
public interface KernelFunction<O extends DatabaseObject, D extends Distance<D>> extends PrimitiveSimilarityFunction<O, D>, PrimitiveDistanceFunction<O, D> {
	//TODO any methods?
}
