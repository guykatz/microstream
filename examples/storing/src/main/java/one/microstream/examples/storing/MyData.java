
package one.microstream.examples.storing;

/*-
 * #%L
 * microstream-examples-storing
 * %%
 * Copyright (C) 2019 - 2022 MicroStream Software
 * %%
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 * 
 * This Source Code may also be made available under the following Secondary
 * Licenses when the conditions for such availability set forth in the Eclipse
 * Public License, v. 2.0 are satisfied: GNU General Public License, version 2
 * with the GNU Classpath Exception which is
 * available at https://www.gnu.org/software/classpath/license.html.
 * 
 * SPDX-License-Identifier: EPL-2.0 OR GPL-2.0 WITH Classpath-exception-2.0
 * #L%
 */

public class MyData
{
	private String name;
	private int    intValue;
	
	public MyData(final String content)
	{
		super();
		this.name = content;
	}
	
	public String getName()
	{
		return this.name;
	}
	
	public void setName(final String name)
	{
		this.name = name;
	}
	
	public int getIntegerValue()
	{
		return this.intValue;
	}
	
	public void setIntValue(final int integerValue)
	{
		this.intValue = integerValue;
	}
	
	@Override
	public String toString()
	{
		return this.name + " value: " + this.intValue;
	}
	
}
