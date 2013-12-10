package de.lmu.ifi.dbs.elki.utilities.optionhandling.parameters;

/*
 This file is part of ELKI:
 Environment for Developing KDD-Applications Supported by Index-Structures

 Copyright (C) 2013
 Ludwig-Maximilians-Universität München
 Lehr- und Forschungseinheit für Datenbanksysteme
 ELKI Development Team

 This program is free software: you can redistribute it and/or modify
 it under the terms of the GNU Affero General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU Affero General Public License for more details.

 You should have received a copy of the GNU Affero General Public License
 along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

import de.lmu.ifi.dbs.elki.utilities.optionhandling.OptionID;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.ParameterException;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.WrongParameterValueException;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.constraints.ParameterConstraint;

/**
 * Parameter class for a parameter specifying an integer value.
 * 
 * @author Steffi Wanka
 * @author Erich Schubert
 */
public class IntParameter extends NumberParameter<Integer> {
  /**
   * Constructs an integer parameter with the given optionID, parameter
   * constraint, and default value.
   * 
   * @param optionID optionID the unique id of the option
   * @param defaultValue the default value
   * @param constraint the constraint for this integer parameter
   * @deprecated Use {@link #addConstraint} instead.
   */
  @Deprecated
  public IntParameter(OptionID optionID, int defaultValue, ParameterConstraint<? super Integer> constraint) {
    super(optionID, Integer.valueOf(defaultValue));
    addConstraint(constraint);
  }

  /**
   * Constructs an integer parameter with the given optionID, parameter
   * constraint, and optional flag.
   * 
   * @param optionID optionID the unique id of the option
   * @param constraint the constraint for this integer parameter
   * @param optional specifies if this parameter is an optional parameter
   * @deprecated Use {@link #addConstraint} instead.
   */
  @Deprecated
  public IntParameter(OptionID optionID, ParameterConstraint<? super Integer> constraint, boolean optional) {
    super(optionID, optional);
    addConstraint(constraint);
  }

  /**
   * Constructs an integer parameter with the given optionID, and parameter
   * constraint.
   * 
   * @param optionID optionID the unique id of the option
   * @param constraint the constraint for this integer parameter
   * @deprecated Use {@link #addConstraint} instead.
   */
  @Deprecated
  public IntParameter(OptionID optionID, ParameterConstraint<? super Integer> constraint) {
    super(optionID);
    addConstraint(constraint);
  }

  /**
   * Constructs an integer parameter with the given optionID.
   * 
   * @param optionID optionID the unique id of the option
   * @param defaultValue the default value
   */
  public IntParameter(OptionID optionID, int defaultValue) {
    super(optionID, Integer.valueOf(defaultValue));
  }

  /**
   * Constructs an integer parameter with the given optionID.
   * 
   * @param optionID optionID the unique id of the option
   * @param optional specifies if this parameter is an optional parameter
   * @deprecated Use {@link #setOptional} instead.
   */
  @Deprecated
  public IntParameter(OptionID optionID, boolean optional) {
    super(optionID, optional);
  }

  /**
   * Constructs an integer parameter with the given optionID.
   * 
   * @param optionID optionID the unique id of the option
   */
  public IntParameter(OptionID optionID) {
    super(optionID);
  }

  @Override
  public String getValueAsString() {
    return getValue().toString();
  }

  @Override
  protected Integer parseValue(Object obj) throws ParameterException {
    if (obj instanceof Integer) {
      return (Integer) obj;
    }
    try {
      return Integer.valueOf(obj.toString());
    } catch (NullPointerException e) {
      throw new WrongParameterValueException("Wrong parameter format! Parameter \"" + getName() + "\" requires an integer value, read: " + obj + "!\n");
    } catch (NumberFormatException e) {
      throw new WrongParameterValueException("Wrong parameter format! Parameter \"" + getName() + "\" requires an integer value, read: " + obj + "!\n");
    }
  }

  /**
   * Returns a string representation of the parameter's type.
   * 
   * @return &quot;&lt;int&gt;&quot;
   */
  @Override
  public String getSyntax() {
    return "<int>";
  }

  /**
   * Get the parameter value as integer
   * 
   * @return Parameter value
   */
  public int intValue() {
    return getValue().intValue();
  }
}
