package net.jadoth.network.persistence;

import static net.jadoth.X.notNull;

import java.nio.channels.SocketChannel;

import net.jadoth.persistence.types.PersistenceManager;


/**
 * Fancily named usability wrapper for a {@link PersistenceManager} in the context of a network connection.
 * 
 * @author TM
 */
public interface ComChannel
{
	public Object receive();
	
	public void send(Object graphRoot);
	
	
	
	
	public static ComChannel.Creator Creator()
	{
		return new ComChannel.Creator.Implementation();
	}
	
	public interface Creator
	{
		public ComChannel createChannel(SocketChannel socketChannel);
		
		
		
		public final class Implementation implements ComChannel.Creator
		{
			Implementation()
			{
				super();
			}

			@Override
			public ComChannel createChannel(final SocketChannel socketChannel)
			{
				/* (01.11.2018 TM)FIXME: ComChannel.Creator#createChannel()
				 * 
				 * Code from proof-of-concept:
				 */
//				final NetworkPersistenceChannelBinary channel = NetworkPersistenceChannelBinary.New(
//					socketChannel,
//					BufferSizeProvider.New()
//				);
//
//				final BinaryPersistenceFoundation<?> foundation = createFoundation(systemDirectory, isClient);
//				foundation.setPersistenceChannel(channel);
//
//				final PersistenceManager<Binary> pm = foundation.createPersistenceManager();
//
//				return ComChannel.New(pm);
				
				throw new net.jadoth.meta.NotImplementedYetError();
			}
			
		}
		
	}
	
	public static ComChannel New(final PersistenceManager<?> persistenceManager)
	{
		return new Implementation(
			notNull(persistenceManager)
		);
	}
	
	public final class Implementation implements ComChannel
	{
		///////////////////////////////////////////////////////////////////////////
		// instance fields //
		////////////////////
		
		private final PersistenceManager<?> persistenceManager;
		
		
		
		///////////////////////////////////////////////////////////////////////////
		// constructors //
		/////////////////

		Implementation(final PersistenceManager<?> persistenceManager)
		{
			super();
			this.persistenceManager = persistenceManager;
		}
		
		
		
		///////////////////////////////////////////////////////////////////////////
		// methods //
		////////////
		
		@Override
		public final void send(final Object graphRoot)
		{
			/*
			 * "store" is a little unfitting here.
			 * However, technically, it is correct. The graph is "stored" (written) to the network connection.
			 */
			this.persistenceManager.store(graphRoot);
		}
		
		@Override
		public final Object receive()
		{
			/*
			 * in the context of a network connection, the generic get() means
			 * receive whatever the other side is sending.
			 */
			return this.persistenceManager.get();
		}
		
	}
	
}
