package one.microstream.integrations.cdi.types.config.test;

/*-
 * #%L
 * microstream-integrations-cdi3
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

import one.microstream.integrations.cdi.types.config.StorageManagerInitializer;
import one.microstream.storage.types.StorageManager;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class SomeStorageManagerInitializer implements StorageManagerInitializer
{

    private boolean initializerCalled;

    private boolean managerRunning;

    @Override
    public void initialize(final StorageManager storageManager)
    {
        this.initializerCalled = true;
        this.managerRunning = storageManager.isRunning();
    }

    public boolean isInitializerCalled()
    {
        return initializerCalled;
    }

    public boolean isManagerRunning()
    {
        return managerRunning;
    }
}
