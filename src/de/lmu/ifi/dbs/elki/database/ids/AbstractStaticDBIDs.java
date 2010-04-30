package de.lmu.ifi.dbs.elki.database.ids;

import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;

/**
 * Static (no modifications allowed) set of Database Object IDs.
 * 
 * @author Erich Schubert
 */
public abstract class AbstractStaticDBIDs implements StaticDBIDs, Collection<DBID> {
  @Override
  public Collection<DBID> asCollection() {
    return Collections.unmodifiableCollection(this);
  }

  @Override
  public boolean containsAll(Collection<?> c) {
    Iterator<?> e = c.iterator();
    while(e.hasNext()) {
      if(!contains(e.next())) {
        return false;
      }
    }
    return true;
  }

  @Override
  public boolean isEmpty() {
    return size() == 0;
  }

  /*
   * To Array operations
   */
  @Override
  public Object[] toArray() {
    return toArray(new Object[size()]);
  }

  /*
   * Unsupported operations (modifications are not allowed)
   */
  @Override
  public boolean add(@SuppressWarnings("unused") DBID e) {
    throw new UnsupportedOperationException(AbstractStaticDBIDs.class.getName() + " are unmodifiable!");
  }

  @Override
  public boolean addAll(@SuppressWarnings("unused") Collection<? extends DBID> c) {
    throw new UnsupportedOperationException(AbstractStaticDBIDs.class.getName() + " are unmodifiable!");
  }

  @Override
  public boolean remove(@SuppressWarnings("unused") Object o) {
    throw new UnsupportedOperationException(AbstractStaticDBIDs.class.getName() + " are unmodifiable!");
  }

  @Override
  public boolean removeAll(@SuppressWarnings("unused") Collection<?> c) {
    throw new UnsupportedOperationException(AbstractStaticDBIDs.class.getName() + " are unmodifiable!");
  }

  @Override
  public boolean retainAll(@SuppressWarnings("unused") Collection<?> c) {
    throw new UnsupportedOperationException(AbstractStaticDBIDs.class.getName() + " are unmodifiable!");
  }

  @Override
  public void clear() {
    throw new UnsupportedOperationException(AbstractStaticDBIDs.class.getName() + " are unmodifiable!");
  }
}