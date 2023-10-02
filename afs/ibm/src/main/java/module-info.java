/*-
 * #%L
 * microstream-afs-ibm-cos
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
module microstream.afs.ibm.cos
{
	exports one.microstream.afs.ibm.cos.types;

	requires microstream.configuration;
	requires microstream.afs;
	requires ibm.cos.java.sdk.core;
	requires ibm.cos.java.sdk;
	requires ibm.cos.java.sdk.s3;
	requires ibm.cos.java.sdk.bundle;
	requires microstream.afs.blobstore;
}
