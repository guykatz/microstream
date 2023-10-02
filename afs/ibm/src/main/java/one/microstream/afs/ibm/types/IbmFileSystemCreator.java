package one.microstream.afs.ibm.types;

/*-
 * #%L
 * microstream-afs-ibm
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

import com.ibm.cloud.objectstorage.auth.BasicAWSCredentials;
import com.ibm.cloud.objectstorage.auth.DefaultAWSCredentialsProviderChain;
import com.ibm.cloud.objectstorage.auth.EnvironmentVariableCredentialsProvider;
import com.ibm.cloud.objectstorage.auth.SystemPropertiesCredentialsProvider;
import one.microstream.afs.types.AFileSystem;
import one.microstream.configuration.types.Configuration;
import one.microstream.configuration.types.ConfigurationBasedCreator;
import com.ibm.cloud.objectstorage.client.builder.AwsClientBuilder;


public abstract class IbmFileSystemCreator extends ConfigurationBasedCreator.Abstract<AFileSystem>
{
	protected IbmFileSystemCreator()
	{
		super(AFileSystem.class);
	}

	protected void populateBuilder(
		final AwsClientBuilder<?, ?> clientBuilder,
		final Configuration configuration
	)
	{
		//TODO
//		configuration.opt("endpoint-override").ifPresent(endpointOverride ->
//		{
//			try
//			{
//				clientBuilder.setEndpointConfiguration(new URI(endpointOverride));
//			}
//			catch(final URISyntaxException e)
//			{
//				throw new ConfigurationException(configuration, e);
//			}
//		});
		configuration.opt("region").ifPresent(
			region -> clientBuilder.setRegion(region)
		);
		configuration.opt("credentials.type").ifPresent(credentialsType ->
		{
			switch(credentialsType)
			{
				case "environment-variables":
				{
					clientBuilder.setCredentials(new EnvironmentVariableCredentialsProvider());
				}
				break;

				case "system-properties":
				{
					clientBuilder.setCredentials(new SystemPropertiesCredentialsProvider());
				}
				break;

				case "static":
				{
					clientBuilder.setCredentials(
						new com.ibm.cloud.objectstorage.auth.AWSStaticCredentialsProvider(
							new BasicAWSCredentials(
								configuration.get("credentials.access-key-id"),
								configuration.get("credentials.secret-access-key")
							)
						)
					);
				}
				break;

				case "default":
				{
					clientBuilder.setCredentials(new DefaultAWSCredentialsProviderChain());
				}
				break;

				default:
					// no credentials provider is used if not explicitly set
			}
		});
	}

}
