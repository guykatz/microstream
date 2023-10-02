package one.microstream.afs.ibm.cos.types;

/*-
 * #%L
 * microstream-afs-ibm-ocs
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

import com.ibm.cloud.objectstorage.client.builder.AwsClientBuilder;
import com.ibm.cloud.objectstorage.services.s3.AmazonS3;
import com.ibm.cloud.objectstorage.services.s3.AmazonS3Client;
import com.ibm.cloud.objectstorage.services.s3.AmazonS3ClientBuilder;
import one.microstream.afs.blobstore.types.BlobStoreFileSystem;
import one.microstream.afs.types.AFileSystem;
import one.microstream.configuration.types.Configuration;

public class CosFileSystemCreator extends IbmFileSystemCreator
{
	public CosFileSystemCreator()
	{
		super();
	}
	
	@Override
	public AFileSystem create(
		final Configuration configuration
	)
	{
		final Configuration s3Configuration = configuration.child("ibm.cos");
		if(s3Configuration == null)
		{
			return null;
		}

		final AwsClientBuilder<AmazonS3ClientBuilder, AmazonS3> clientBuilder = AmazonS3Client.builder();
		this.populateBuilder(clientBuilder, s3Configuration);
		
		final AmazonS3 client    = clientBuilder.build();
		final boolean        cache     = configuration.optBoolean("cache").orElse(true);
		final CosConnector   connector = cache
			? CosConnector.Caching(client)
			: CosConnector.New(client)
		;
		return BlobStoreFileSystem.New(connector);
	}
	
}
