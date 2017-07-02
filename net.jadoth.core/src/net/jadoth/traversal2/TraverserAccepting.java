package net.jadoth.traversal2;

public interface TraverserAccepting<T>
{
	public void traverseReferences(T instance, TraversalAcceptor acceptor, TraversalEnqueuer enqueuer);
	
	
	public interface Creator
	{
		public <T> TraverserAccepting<T> createTraverserAccepting(Class<T> type);
				
	}
	
}
