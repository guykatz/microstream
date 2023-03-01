package one.microstream.experimental.binaryread.storage.reader.helper;

/*-
 * #%L
 * binary-read
 * %%
 * Copyright (C) 2019 - 2023 MicroStream Software
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

import java.util.Map;

public class KeyValueEntry<K, V> implements Map.Entry<K, V>
{
    private final K key;
    private V value;
    private final String delimiter;

    public KeyValueEntry(final K key, final V value)
    {
       this(key, value, null);
    }

    public KeyValueEntry(final K key, final V value, final String delimiter)
    {
        this.key = key;
        this.value = value;
        this.delimiter = delimiter;
    }

    @Override
    public K getKey()
    {
        return key;
    }

    @Override
    public V getValue()
    {
        return value;
    }

    @Override
    public V setValue(final V newValue)
    {
        V oldValue = value;
        value = newValue;
        return oldValue;
    }

    @Override
    public String toString()
    {
        return String.format("[%s%s%s]", key, delimiter, value);
    }
}
