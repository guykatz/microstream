package net.jadoth.traversal2;

import net.jadoth.collections.types.XGettingCollection;


public final class TraverserXCollectionImmutable implements TraversalHandler
{
	@SuppressWarnings("unchecked")
	@Override
	public final void traverseReferences(
		final Object            instance,
		final TraversalAcceptor acceptor,
		final TraversalEnqueuer enqueuer
	)
	{
		try
		{
			((XGettingCollection<Object>)instance).iterate(current ->
			{
				final Object returned;
				if((returned = acceptor.acceptInstance(current, instance, enqueuer)) != current)
				{
					throw new UnsupportedOperationException();
				}
				
				// note: if the current (now prior) value has to be enqueued, the acceptor can do that internally
				enqueuer.enqueue(returned);
			});
		}
		catch(final AbstractTraversalSkipSignal s)
		{
			// any skip signal reaching this point means abort the whole instance, in one way or another
		}
		
	}
	
}
