package de.lmu.ifi.dbs.elki.logging.progress;

import java.util.logging.Level;

import de.lmu.ifi.dbs.elki.logging.ELKILogRecord;

/**
 * Log record for progress messages.
 * 
 * @author Erich Schubert
 * 
 * @apivis.has Progress
 */
public class ProgressLogRecord extends ELKILogRecord {
  /**
   * Serial version
   */
  private static final long serialVersionUID = 1L;
  
  /**
   * Progress storage
   */
  private final Progress progress;

  /**
   * Constructor for progress log messages.
   * 
   * @param level Logging level
   * @param progress Progress to log
   */
  public ProgressLogRecord(Level level, Progress progress) {
    super(level, null);
    this.progress = progress;
    this.setMessage(progress.toString());
  }

  /**
   * Get the objects progress.
   * 
   * @return the progress
   */
  public Progress getProgress() {
    return progress;
  }
}