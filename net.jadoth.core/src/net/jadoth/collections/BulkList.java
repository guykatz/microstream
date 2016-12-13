package net.jadoth.collections;

import static net.jadoth.Jadoth.checkArrayRange;
import static net.jadoth.collections.JadothArrays.removeAllFromArray;

import java.util.Comparator;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.NoSuchElementException;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

import net.jadoth.Jadoth;
import net.jadoth.collections.old.AbstractBridgeXList;
import net.jadoth.collections.types.IdentityEqualityLogic;
import net.jadoth.collections.types.XGettingCollection;
import net.jadoth.collections.types.XGettingSequence;
import net.jadoth.collections.types.XList;
import net.jadoth.exceptions.ArrayCapacityException;
import net.jadoth.exceptions.IndexBoundsException;
import net.jadoth.functional.Aggregator;
import net.jadoth.functional.BiProcedure;
import net.jadoth.functional.IndexProcedure;
import net.jadoth.functional.JadothEqualators;
import net.jadoth.math.JadothMath;
import net.jadoth.util.Composition;
import net.jadoth.util.Equalator;
import net.jadoth.util.iterables.GenericListIterator;


/**
 * Full scale general purpose implementation of extended collection type {@link XList}.
 * <p>
 * This array-backed implementation is optimal for all needs of a list that do not require frequent structural
 * modification (insert or remove) of single elements before the end of the list.<br>
 * It is recommended to use this implementation as default list type until concrete performance deficiencies are
 * identified. If used properly (e.g. always ensure enough capacity, make use of batch procedures like
 * {@link #inputAll(long, Object...)}, {@link #removeRange(long, long)}, etc.), this implementation has equal or
 * massively superior performance to linked-list implementation is most cases.
 * <p>
 * This implementation is NOT synchronized and thus should only be used by a
 * single thread or in a thread-safe manner (i.e. read-only as soon as multiple threads access it).<br>
 * See {@link SynchList} wrapper class to use a list in a synchronized manner.
 * <p>
 * Note that this List implementation does NOT keep track of modification count as JDK's collection implementations do
 * (and thus never throws a {@link ConcurrentModificationException}), for two reasons:<br>
 * 1.) It is already explicitely declared thread-unsafe and for single-thread (or thread-safe)
 * use only.<br>
 * 2.) The common modCount-concurrency exception behaviour ("failfast") has buggy and inconsistent behaviour by
 * throwing {@link ConcurrentModificationException} even in single thread use, i.e. when iterating over a collection
 * and removing more than one element of it without using the iterator's method.<br>
 * <br>
 * Current conclusion is that the JDK's failfast implementations buy unneeded (and even unreliable as stated by
 * official guides) concurrency modification recognition at the cost of performance loss and even a bug when already
 * used in a thread-safe manner.
 * <p>
 * Also note that by being an extended collection, this implementation offers various functional and batch procedures
 * to maximize internal iteration potential, eliminating the need to use the ill-conceived external iteration
 * {@link Iterator} paradigm.
 *
 * @author Thomas Muenz
 * @version 0.95, 2011 - 12 - 12
 */
public final class BulkList<E> extends AbstractSimpleArrayCollection<E>
implements XList<E>, Composition, IdentityEqualityLogic
{
	///////////////////////////////////////////////////////////////////////////
	// constants        //
	/////////////////////

	static final int DEFAULT_INITIAL_CAPACITY = 1;



	///////////////////////////////////////////////////////////////////////////
	//  static methods   //
	/////////////////////

	public static <E> Function<BulkList<E>, ConstList<E>> Immurer()
	{
		return b -> b.immure();
	}

	public static <E> Aggregator<E, BulkList<E>> Builder()
	{
		return Builder(DEFAULT_INITIAL_CAPACITY);
	}

	public static <E> Aggregator<E, BulkList<E>> Builder(final long initialCapacity)
	{
		return new Aggregator<E, BulkList<E>>()
		{
			private final BulkList<E> newInstance = BulkList.New(initialCapacity);

			@Override
			public final void accept(final E element)
			{
				this.newInstance.add(element);
			}

			@Override
			public final BulkList<E> yield()
			{
				return this.newInstance;
			}
		};
	}

	public static final <E> BulkList<E> New()
	{
		return new BulkList<>();
	}

	public static final <E> BulkList<E> New(final long initialCapacity)
	{
		return new BulkList<>(checkArrayRange(initialCapacity));
	}

	// just New(E) confuses the compiler with New(int) when using ::New and causes ambiguity with New(E...)
	public static final <E> BulkList<E> NewFromSingle(final E initialElement)
	{
		return new BulkList<>(initialElement);
	}

	@SafeVarargs
	public static final <E> BulkList<E> New(final E... initialElements)
	{
		return new BulkList<>(initialElements);
	}

	public static final <E> BulkList<E> New(final XIterable<E> initialElements)
	{
		final BulkList<E> newInstance = new BulkList<>();
		initialElements.iterate(newInstance::add);
		return newInstance;
	}

	public static final <E> BulkList<E> New(final XGettingCollection<E> initialElements)
	{
		return new BulkList<E>(Jadoth.to_int(initialElements.size())).addAll(initialElements);
	}



	///////////////////////////////////////////////////////////////////////////
	// instance fields //
	////////////////////

	E[] data; // the storage array containing the elements
	int size; // the current element count (logical size)



	///////////////////////////////////////////////////////////////////////////
	// constructors //
	/////////////////

	/**
	 * Default constructor instantiating an empty instance with default (minimum) capacity.
	 */
	public BulkList()
	{
		super();
		this.size = 0;
		this.data = newArray(DEFAULT_INITIAL_CAPACITY);
	}

	public BulkList(final E initialElement)
	{
		super();
		this.size = 1;
		(this.data = newArray(DEFAULT_INITIAL_CAPACITY))[0] = initialElement;
	}

	/**
	 * Initial capacity constructor instantiating an empty instance with a given initial capacity.
	 * <p>
	 * The actual initial capacity is the highest of the following three values:
	 * <ul>
	 * <li>{@link Integer.MAX_VALUE}, if the given initial capacity is greater than 2^30.</li>
	 * <li>The lowest power of two value that is equal to or greater than the given initial capacity.</li>
	 * <li>The default (minimum) capacity.</li>
	 * </ul>
	 *
	 * @param initialCapacity the desired custom initial capacity.
	 */
	public BulkList(final int initialCapacity)
	{
		super();
		this.size = 0;
		this.data = newArray(JadothMath.pow2BoundMaxed(initialCapacity));
	}

	/**
	 * Copy constructor that instantiates a new instance with a copy of the passed original instance's data and same
	 * size.
	 *
	 * @param original the instance to be copied.
	 * @throws NullPointerException if {@code null} was passed.
	 *
	 * @see #copy()
	 */
	public BulkList(final BulkList<? extends E> original) throws NullPointerException
	{
		super();
		this.size = original.size;
		this.data = original.data.clone();
	}

	/**
	 * Convenience initial data constructor, instantiating a new instance containing all elements of the passed
	 * array. The element size of the new instance will be equal to the passed array's length.
	 * <p>
	 * Note that providing no element at all in the VarArgs parameter will automatically cause the
	 * default constructor {@link #BulkList()} to be used instead. Explicitely providing an {@code null} array
	 * reference will cause a {@link NullPointerException}.
	 *
	 * @param elements the initial elements for the new instance.
	 * @throws NullPointerException if an explicit {@code null} array reference was passed.
	 *
	 * @see #BulkList()
	 */
	@SafeVarargs
	public BulkList(final E... elements) throws NullPointerException
	{
		super();
		System.arraycopy(
			elements,
			0,
			this.data = newArray(JadothMath.pow2BoundMaxed(this.size = elements.length)),
			0,
			this.size
		);
	}

	/**
	 * Detailed initializing constructor allowing to specify initial capacity and a custom array range of initial data.
	 * <p>
	 * The actual initial capacity will be calculated based on the higher of the two values {@code initialCapacity}
	 * and {@code srcLength} as described in {@link #BulkList(int)}.
	 * <p>
	 * The specified initial elements array range is copied via {@link System#arraycopy(Object, int, Object, int, int)}.
	 *
	 * @param initialCapacity the desired initial capacity for the new instance.
	 * @param src the source array containg the desired range of initial elements.
	 * @param srcStart the start index of the desired range of initial elements in the source array.
	 * @param srcLength the length of the desired range of initial elements in the source array.
	 */
	public BulkList(final int initialCapacity, final E[] src, final int srcStart, final int srcLength)
	{
		super();
		System.arraycopy(
			src,
			srcStart,
			this.data = newArray(JadothMath.pow2BoundMaxed(initialCapacity >= srcLength ? initialCapacity : srcLength)),
			0,
			this.size = srcLength
		);
	}

	/**
	 * Internal constructor to directly supply the storage array instance and size.
	 * <p>
	 * The passed storage array must comply to the power of two aligned size rules as specified in
	 * {@link #BulkList(int)} and the size must be consistent to the storage array.<br>
	 * Calling this constructor without complying to these rules will result in a broken instance.
	 * <p>
	 * It is recommended to NOT use this constructor outside collections-framework-internal implementations.
	 *
	 * @param storageArray the array to be used as the storage for the new instance.
	 * @param size the element size of the new instance.
	 */
	BulkList(final E[] storageArray, final int size)
	{
		super();
		this.size = size;
		this.data = storageArray;
	}



	///////////////////////////////////////////////////////////////////////////
	// declared methods //
	/////////////////////

	private void checkSizeIncreasable()
	{
		if(this.size >= Integer.MAX_VALUE)
		{
			// (26.10.2013)XXX: replace all noobish IndexOutOfBoundsException throughout all projects
//			throw new IndexOutOfBoundsException();
			throw new ArrayCapacityException();
		}
	}

	/* this method is highly optimized for performance, yielding up to around 300% the speed of
	 * java.util.ArrayList.add() when adding elements to an already big enough storage.
	 * Moving the storage increase part to a private increaseStorage() would make it faster when
	 * regular increasing is needed, but puzzlingly then the alreay-big-enough performance
	 * advantage drops to around 110% faster instead of 300% faster (even though the single not called
	 * increase method should be removed by HotSpot compiling. Seems there is a bug or at least
	 * some heavy confusion going on there.
	 * As a consequence, storage increasing has NOT been moved to a private method, thus maintaining
	 * the huge alreay-big-enough performance advantage, but making it slower in regular-growth-cases
	 * (also very strange).
	 * Maybe one of the two HotSpot compiling problems improves in the future, so that both cases
	 * of advanced performance are reachable by optimization.
	 */
	void internalAdd(final E element)
	{
		/* notes on algorithm:
		 * - " >= " is significantly faster than " == ", probably due to simple sign bit checking?
		 * - assignment inlining increases normal case performance by >10% ^^
		 * - float conversion is automatically capped at MAX_VALUE, whereas "<<= 1" can only reach 2^30 and then crash
		 * - "<<= 1" would speed up normal case by ~5%, but would limit list size to 2^30 instead of MAX_VALUE
		 * - " + +this.lastIndex" would be ~5% faster than "this.size+ + ", but would complicate every use of list's size
		 */
		if(this.size >= this.data.length)
		{
			if(this.size >= Integer.MAX_VALUE)
			{
				throw new CapacityExceededException();
			}
			System.arraycopy(this.data, 0, this.data = newArray((int)(this.data.length * 2.0f)), 0, this.size);
		}
		this.data[this.size++] = element;
	}

	private int internalInputArray(final int index, final Object[] elements, final int elementsSize)
	{
		// check for simple case without a required capacity increase
		if(this.data.length - this.size >= elementsSize)
		{
			// simply free up enough space at index and slide in new elements
			System.arraycopy(this.data, index, this.data, index + elementsSize, elementsSize);
			System.arraycopy(elements ,     0, this.data, index               , elementsSize);
			this.size += elementsSize;
			return elementsSize;
		}

		// overflow-safe check for unreachable capacity
		if(Integer.MAX_VALUE - this.size < elementsSize)
		{
			// unreachable capacity
			throw new ArrayCapacityException((long)elementsSize + this.size);
		}

		// required and reachable capacity increase
		final int newSize = this.size + elementsSize;
		int newCapacity;
		if(JadothMath.isGreaterThanHighestPowerOf2Integer(newSize))
		{
			// JVM technical limit
			newCapacity = Integer.MAX_VALUE;
		}
		else
		{
			newCapacity = this.data.length;
			while(newCapacity < newSize)
			{
				newCapacity <<= 1;
			}
		}

		/* copy elements in two steps:
		 *        old array             new array
		 * 1.) [    0; index] -> [        0;    index]
		 * 2.) [index;  size] -> [index+gap; size+gap]
		 *
		 * So it looks like this:
		 * --- - 1.)----       ----2.)----
		 * |||||||||||_______|||||||||||
		 * where this ^^^^^^^ is exactely enough space (the gap) for inserting "elements"
		 *
		 * this way, all elements are only copied once
		 */
		final E[] data;
		System.arraycopy(this.data,     0, data = newArray(newCapacity), 0, index);
		System.arraycopy(this.data, index, data, index + elementsSize, elementsSize);
		System.arraycopy(elements ,     0, this.data = data,    index, elementsSize);
		this.size = newSize;
		return elementsSize;
	}

	private int internalInputArray(final int index, final E[] elements, final int offset, final int length)
	{
		if(length < 0)
		{
			return this.internalReverseInputArray(index, elements, offset, -length);
		}

		// check for simple case without a required capacity increase
		if(this.data.length - this.size >= length)
		{
			// simply free up enough space at index and slide in new elements
			System.arraycopy(this.data, index, this.data, index + length, length);
			System.arraycopy(elements, offset, this.data, index         , length);
			this.size += length;
			return length;
		}

		// overflow-safe check for unreachable capacity
		if(Integer.MAX_VALUE - this.size < length)
		{
			// unreachable capacity
			throw new ArrayCapacityException((long)length + this.size);
		}

		// required and reachable capacity increase
		final int newSize = this.size + length;
		int newCapacity;
		if(JadothMath.isGreaterThanHighestPowerOf2Integer(newSize))
		{
			// JVM technical limit
			newCapacity = Integer.MAX_VALUE;
		}
		else
		{
			newCapacity = this.data.length;
			while(newCapacity < newSize)
			{
				newCapacity <<= 1;
			}
		}

		/* copy elements in two steps:
		 *        old array             new array
		 * 1.) [    0; index] -> [        0;    index]
		 * 2.) [index;  size] -> [index+gap; size+gap]
		 *
		 * So it looks like this:
		 * --- - 1.)----       ----2.)----
		 * |||||||||||_______|||||||||||
		 * where this ^^^^^^^ is exactely enough space (the gap) for inserting "elements"
		 *
		 * this way, all elements are only copied once
		 */
		final E[] data;
		System.arraycopy(this.data,     0, data = newArray(newCapacity), 0, index);
		System.arraycopy(this.data, index, data, index + length, length);
		System.arraycopy(elements, offset, this.data = data,    index, length);
		this.size = newSize;
		return length;
	}

	private int internalReverseInputArray(final int index, final E[] elements, final int offset, final int length)
	{
		// check for simple case without a required capacity increase
		if(this.data.length - this.size >= length)
		{
			// simply free up enough space at index and slide in new elements
			System.arraycopy(this.data, index, this.data, index + length, length);
			JadothArrays.reverseArraycopy(elements, offset, this.data, index, length);
			this.size += length;
			return length;
		}

		// overflow-safe check for unreachable capacity
		if(Integer.MAX_VALUE - this.size < length)
		{
			// unreachable capacity
			throw new ArrayCapacityException((long)length + this.size);
		}

		// required and reachable capacity increase
		final int newSize = this.size + length;
		int newCapacity;
		if(JadothMath.isGreaterThanHighestPowerOf2Integer(newSize))
		{
			// JVM technical limit
			newCapacity = Integer.MAX_VALUE;
		}
		else
		{
			newCapacity = this.data.length;
			while(newCapacity < newSize)
			{
				newCapacity <<= 1;
			}
		}

		/* copy elements in two steps:
		 *        old array             new array
		 * 1.) [    0; index] -> [        0;    index]
		 * 2.) [index;  size] -> [index+gap; size+gap]
		 *
		 * So it looks like this:
		 * --- - 1.)----       ----2.)----
		 * |||||||||||_______|||||||||||
		 * where this ^^^^^^^ is exactely enough space (the gap) for inserting "elements"
		 *
		 * this way, all elements are only copied once
		 */
		final E[] data;
		System.arraycopy(this.data,     0, data = newArray(newCapacity), 0, index);
		System.arraycopy(this.data, index, data, index + length, length);
		JadothArrays.reverseArraycopy(elements, 0, this.data, index, -length);
		this.size = newSize;
		return length;
	}



	///////////////////////////////////////////////////////////////////////////
	// override methods //
	/////////////////////

	@Override
	protected E[] internalGetStorageArray()
	{
		return this.data;
	}

	@Override
	protected final int internalSize()
	{
		return this.size;
	}

	@Override
	protected int[] internalGetSectionIndices()
	{
		return new int[]{0, this.size}; // trivial section
	}

	@Override
	protected int internalCountingAddAll(final E[] elements) throws UnsupportedOperationException
	{
		this.ensureFreeCapacity(elements.length); // increaseCapacity
		System.arraycopy(elements, 0, this.data, this.size, elements.length);
		this.size += elements.length;
		return elements.length;
	}

	@Override
	protected int internalCountingAddAll(final E[] elements, final int offset, final int length)
		throws UnsupportedOperationException
	{
		if(length >= 0)
		{
			this.ensureFreeCapacity(length); // increaseCapacity
			System.arraycopy(elements, offset, this.data, this.size, length); // automatic bounds checks
			this.size += length;
			return length;
		}

		final int bound;
		if((bound = offset + length) < -1)
		{
			throw new ArrayIndexOutOfBoundsException(bound + 1);
		}
		this.ensureFreeCapacity(-length); // increaseCapacity
		final Object[] data = this.data;
		int size = this.size;
		for(int i = offset; i > bound; i--)
		{
			data[size++] = elements[i];
		}
		this.size = size;
		return -length;
	}

	@Override
	protected int internalCountingAddAll(final XGettingCollection<? extends E> elements)
		throws UnsupportedOperationException
	{
		if(elements instanceof AbstractSimpleArrayCollection)
		{
			return this.internalCountingAddAll(
				AbstractSimpleArrayCollection.internalGetStorageArray((AbstractSimpleArrayCollection<?>)elements),
				0,
				Jadoth.to_int(elements.size())
			);
		}
		final int oldSize = this.size;
		elements.iterate(this);
		return this.size - oldSize;
	}

	@Override
	protected int internalCountingPutAll(final E[] elements) throws UnsupportedOperationException
	{
		this.ensureFreeCapacity(elements.length); // increaseCapacity
		System.arraycopy(elements, 0, this.data, this.size, elements.length);
		this.size += elements.length;
		return elements.length;
	}

	@Override
	protected int internalCountingPutAll(final E[] elements, final int offset, final int length)
		throws UnsupportedOperationException
	{
		if(length >= 0)
		{
			this.ensureFreeCapacity(length); // increaseCapacity
			System.arraycopy(elements, offset, this.data, this.size, length); // automatic bounds checks
			this.size += length;
			return length;
		}

		final int bound;
		if((bound = offset + length) < -1)
		{
			throw new ArrayIndexOutOfBoundsException(bound + 1);
		}
		this.ensureFreeCapacity(-length); // increaseCapacity
		final Object[] data = this.data;
		int size = this.size;
		for(int i = offset; i > bound; i--)
		{
			data[size++] = elements[i];
		}
		this.size = size;
		return -length;
	}

	@Override
	protected int internalCountingPutAll(final XGettingCollection<? extends E> elements)
		throws UnsupportedOperationException
	{
		if(elements instanceof AbstractSimpleArrayCollection<?>)
		{
			return this.internalCountingAddAll(
				AbstractSimpleArrayCollection.internalGetStorageArray((AbstractSimpleArrayCollection<?>)elements),
				0,
				Jadoth.to_int(elements.size())
			);
		}

		final int oldSize = this.size;
		elements.iterate(this);
		return this.size - oldSize;
	}

	@Override
	public final Equalator<? super E> equality()
	{
		return JadothEqualators.identity();
	}



	///////////////////////////////////////////////////////////////////////////
	// getting methods  //
	/////////////////////

	@Override
	public final BulkList<E> copy()
	{
		return new BulkList<>(this);
	}

	@Override
	public final ConstList<E> immure()
	{
		return new ConstList<>(this);
	}

	@Override
	public final BulkList<E> toReversed()
	{
		final E[] data, reversedData = newArray((data = this.data).length);
		for(int i = this.size, r = 0; i-- > 0;)
		{
			reversedData[r++] = data[i];
		}
		return new BulkList<>(reversedData, this.size);
	}

	@Override
	public final E[] toArray(final Class<E> type)
	{
		final E[] array;
		System.arraycopy(this.data, 0, array = JadothArrays.newArray(type, this.size), 0, this.size);
		return array;
	}

	// executing //

	@Override
	public final <P extends Consumer<? super E>> P iterate(final P procedure)
	{
		AbstractArrayStorage.iterate(this.data, this.size, procedure);
		return procedure;
	}

	@Override
	public final <A> A join(final BiProcedure<? super E, ? super A> joiner, final A aggregate)
	{
		AbstractArrayStorage.join(this.data, this.size, joiner, aggregate);
		return aggregate;
	}

	@Override
	public final <P extends IndexProcedure<? super E>> P iterateIndexed(final P procedure)
	{
		AbstractArrayStorage.iterate(this.data, this.size, procedure);
		return procedure;
	}

	@Override
	public final long count(final E element)
	{
		return AbstractArrayStorage.count(this.data, this.size, element);
	}

	@Override
	public final long countBy(final Predicate<? super E> predicate)
	{
		return AbstractArrayStorage.conditionalCount(this.data, this.size, predicate);
	}

	// index querying //

	@Override
	public final long indexOf(final E element)
	{
		return AbstractArrayStorage.indexOf(this.data, this.size, element);
	}

	@Override
	public final long indexBy(final Predicate<? super E> predicate)
	{
		return AbstractArrayStorage.conditionalIndexOf(this.data, this.size, predicate);
	}

	@Override
	public final long lastIndexOf(final E element)
	{
		return AbstractArrayStorage.rngIndexOF(this.data, this.size, this.size - 1, -this.size, element);
	}

	@Override
	public final long lastIndexBy(final Predicate<? super E> predicate)
	{
		return AbstractArrayStorage.lastIndexOf(this.data, this.size, predicate);
	}

	@Override
	public final long maxIndex(final Comparator<? super E> comparator)
	{
		return AbstractArrayStorage.maxIndex(this.data, this.size, comparator);
	}

	@Override
	public final long minIndex(final Comparator<? super E> comparator)
	{
		return AbstractArrayStorage.minIndex(this.data, this.size, comparator);
	}

	@Override
	public final long scan(final Predicate<? super E> predicate)
	{
		return AbstractArrayStorage.scan(this.data, this.size, predicate);
	}

	// element querying //

	@Override
	public final E get() throws NoSuchElementException
	{
		if(this.isEmpty())
		{
			throw new NoSuchElementException();
		}
		return this.data[0];
	}

	@Override
	public final E first() throws IndexBoundsException
	{
		if(this.isEmpty())
		{
			throw new IndexBoundsException(0, 0);
		}
		return this.data[0];
	}

	@Override
	public final E last() throws IndexBoundsException
	{
		if(this.isEmpty())
		{
			throw new IndexBoundsException(0, 0);
		}
		return this.data[this.size - 1];
	}

	@Override
	public final E poll()
	{
		return this.size == 0 ? null : this.data[0];
	}

	@Override
	public final E peek()
	{
		return this.size == 0 ? null : this.data[this.size - 1];
	}

	@Override
	public final E search(final Predicate<? super E> predicate)
	{
		return AbstractArrayStorage.searchElement(this.data, this.size, predicate, null);
	}

	@Override
	public final E seek(final E sample)
	{
		return AbstractArrayStorage.containsSame(this.data, this.size, sample) ? sample : null;
	}

	@Override
	public final E max(final Comparator<? super E> comparator)
	{
		return AbstractArrayStorage.max(this.data, this.size, comparator);
	}

	@Override
	public final E min(final Comparator<? super E> comparator)
	{
		return AbstractArrayStorage.min(this.data, this.size, comparator);
	}

	// boolean querying //

	@Override
	public final boolean hasVolatileElements()
	{
		return false;
	}

	@Override
	public final boolean nullAllowed()
	{
		return true;
	}

	@Override
	public final boolean isSorted(final Comparator<? super E> comparator)
	{
		return AbstractArrayStorage.isSorted(this.data, this.size, comparator);
	}

//	@Override
//	public final boolean hasDistinctValues()
//	{
//		return AbstractArrayStorage.hasDistinctValues(this.data, this.size);
//	}
//
//	@Override
//	public final boolean hasDistinctValues(final Equalator<? super E> equalator)
//	{
//		return AbstractArrayStorage.hasDistinctValues(this.data, this.size, equalator);
//	}

	// boolean querying - applies //

	@Override
	public final boolean containsSearched(final Predicate<? super E> predicate)
	{
		return AbstractArrayStorage.contains(this.data, this.size, predicate);
	}

	@Override
	public final boolean applies(final Predicate<? super E> predicate)
	{
		return AbstractArrayStorage.applies(this.data, this.size, predicate);
	}

	// boolean querying - contains //

	@Override
	public final boolean nullContained()
	{
		return AbstractArrayStorage.nullContained(this.data, this.size);
	}

	@Override
	public final boolean containsId(final E element)
	{
		return AbstractArrayStorage.containsSame(this.data, this.size, element);
	}

	@Override
	public final boolean contains(final E element)
	{
		return AbstractArrayStorage.containsSame(this.data, this.size, element);
	}

	@Override
	public final boolean containsAll(final XGettingCollection<? extends E> elements)
	{
		return AbstractArrayStorage.containsAll(this.data, this.size, elements);
	}

	// boolean querying - equality //

	@Override
	public final boolean equals(final XGettingCollection<? extends E> samples, final Equalator<? super E> equalator)
	{
		if(samples == null || !(samples instanceof BulkList<?>) || Jadoth.to_int(samples.size()) != this.size)
		{
			return false;
		}
		if(samples == this)
		{
			return true;
		}

		// equivalent to equalsContent()
		return JadothArrays.equals(this.data, 0, ((BulkList<? extends E>)samples).data, 0, this.size, equalator);
	}

	@Override
	public final boolean equalsContent(final XGettingCollection<? extends E> samples, final Equalator<? super E> equalator)
	{
		if(samples == null || Jadoth.to_int(samples.size()) != this.size)
		{
			return false;
		}
		if(samples == this)
		{
			return true;
		}
		return AbstractArrayStorage.equalsContent(this.data, this.size, samples, equalator);
	}

	// data set procedures //

	@Override
	public final <C extends Consumer<? super E>> C intersect(
		final XGettingCollection<? extends E> samples,
		final Equalator<? super E> equalator,
		final C target
	)
	{
		return AbstractArrayStorage.intersect(this.data, this.size, samples, equalator, target);
	}

	@Override
	public final <C extends Consumer<? super E>> C except(
		final XGettingCollection<? extends E> samples,
		final Equalator<? super E> equalator,
		final C target
	)
	{
		return AbstractArrayStorage.except(this.data, this.size, samples, equalator, target);
	}

	@Override
	public final <C extends Consumer<? super E>> C union(
		final XGettingCollection<? extends E> samples,
		final Equalator<? super E> equalator,
		final C target
	)
	{
		return AbstractArrayStorage.union(this.data, this.size, samples, equalator, target);
	}

	@Override
	public final <C extends Consumer<? super E>> C copyTo(final C target)
	{
		return AbstractArrayStorage.copyTo(this.data, this.size, target);
	}

	@Override
	public final <C extends Consumer<? super E>> C filterTo(final C target, final Predicate<? super E> predicate)
	{
		return AbstractArrayStorage.copyTo(this.data, this.size, target, predicate);
	}

	@Override
	public final <T> T[] copyTo(final T[] target, final int offset)
	{
		System.arraycopy(this.data, 0, target, offset, this.size);
		return target;
	}

	@Override
	public final <T> T[] copyTo(final T[] target, final int targetOffset, final long offset, final int length)
	{
		return AbstractArrayStorage.rngCopyTo(
			this.data                     ,
			this.size                     ,
			Jadoth.checkArrayRange(offset),
			length                        ,
			target                        ,
			targetOffset
		);
	}

	@Override
	public final <C extends Consumer<? super E>> C distinct(final C target)
	{
		return AbstractArrayStorage.distinct(this.data, this.size, target);
	}

	@Override
	public final <C extends Consumer<? super E>> C distinct(final C target, final Equalator<? super E> equalator)
	{
		return AbstractArrayStorage.distinct(this.data, this.size, target, equalator);
	}

	@Override
	public final <C extends Consumer<? super E>> C copySelection(final C target, final long... indices)
	{
		return AbstractArrayStorage.copySelection(this.data, this.size, indices, target);
	}



	///////////////////////////////////////////////////////////////////////////
	// setting methods  //
	/////////////////////

	@Override
	public final ListView<E> view()
	{
		return new ListView<>(this);
	}

	@Override
	public final SubListView<E> view(final long fromIndex, final long toIndex)
	{
		return new SubListView<>(this, fromIndex, toIndex); // range check is done in constructor
	}

	@Override
	public final BulkList<E> shiftTo(final long sourceIndex, final long targetIndex)
	{
		if(sourceIndex >= this.size)
		{
			throw new IndexExceededException(this.size, sourceIndex);
		}
		if(targetIndex >= this.size)
		{
			throw new IndexExceededException(this.size, targetIndex);
		}
		if(sourceIndex == targetIndex)
		{
			if(sourceIndex < 0)
			{
				throw new IndexExceededException(this.size, sourceIndex);
			}
			return this;
		}

		final E shiftling = this.data[(int)sourceIndex];
		if(sourceIndex < targetIndex)
		{
			System.arraycopy(this.data, (int)sourceIndex + 1, this.data, (int)sourceIndex, (int)targetIndex - (int)sourceIndex);
		}
		else
		{
			System.arraycopy(this.data, (int)targetIndex, this.data, (int)targetIndex + 1, (int)sourceIndex - (int)targetIndex);
		}

		this.data[(int)targetIndex] = shiftling;
		return this;
	}

	@Override
	public final BulkList<E> shiftTo(final long sourceIndex, final long targetIndex, final long length)
	{
		if(sourceIndex + length >= this.size)
		{
			throw new IndexExceededException(this.size, sourceIndex);
		}
		if(targetIndex + length >= this.size)
		{
			throw new IndexExceededException(this.size, targetIndex);
		}
		if(sourceIndex == targetIndex)
		{
			if(sourceIndex < 0)
			{
				throw new IndexExceededException(this.size, sourceIndex);
			}

			return this;
		}

		final E[] shiftlings;
		System.arraycopy(this.data, (int)sourceIndex, shiftlings = newArray((int)length), 0, (int)length);
		if(sourceIndex < targetIndex)
		{
			System.arraycopy(this.data, (int)(sourceIndex + length), this.data, (int)sourceIndex, (int)(targetIndex - sourceIndex));
		}
		else
		{
			System.arraycopy(this.data, (int)targetIndex, this.data, (int)(targetIndex + length), (int)(sourceIndex - targetIndex));
		}

		System.arraycopy(shiftlings, 0, this.data, (int)targetIndex, (int)length);

		return this;
	}

	@Override
	public final BulkList<E> shiftBy(final long sourceIndex, final long distance)
	{
		return this.shiftTo(sourceIndex, sourceIndex + distance);
	}

	@Override
	public final BulkList<E> shiftBy(final long sourceIndex, final long distance, final long length)
	{
		return this.shiftTo(sourceIndex, sourceIndex + distance, length);
	}

	@Override
	public final BulkList<E> swap(final long indexA, final long indexB)
		throws IndexOutOfBoundsException, ArrayIndexOutOfBoundsException
	{
		validateIndex(this.size, indexA);
		validateIndex(this.size, indexB);

		final E t = this.data[(int)indexA];
		this.data[(int)indexA] = this.data[(int)indexB];
		this.data[(int)indexB] = t;

		return this;
	}

	@Override
	public final BulkList<E> swap(final long indexA, final long indexB, final long length)
	{
		AbstractArrayStorage.swap(
			this.data                     ,
			this.size                     ,
			Jadoth.checkArrayRange(indexA),
			Jadoth.checkArrayRange(indexB),
			Jadoth.checkArrayRange(length)
		);
		return this;
	}

	@Override
	public final BulkList<E> reverse()
	{
		AbstractArrayStorage.reverse(this.data, this.size);
		return this;
	}

	// direct setting //

	@Override
	public final void setFirst(final E element)
	{
		this.data[0] = element;
	}

	@Override
	public final void setLast(final E element)
	{
		this.data[this.size - 1] = element;
	}

	@SafeVarargs
	@Override
	public final BulkList<E> setAll(final long offset, final E... elements)
	{
		validateIndex(this.size, offset);
		validateIndex(this.size, offset + elements.length);
		System.arraycopy(elements, 0, this.data, Jadoth.checkArrayRange(offset), elements.length);

		return this;
	}

	@Override
	public final BulkList<E> set(final long offset, final E[] src, final int srcIndex, final int srcLength)
	{
		AbstractArrayStorage.set(this.data, this.size, Jadoth.checkArrayRange(offset), src, srcIndex, srcLength);
		return this;
	}

	@Override
	public final BulkList<E> set(
		final long                          offset        ,
		final XGettingSequence<? extends E> elements      ,
		final long                          elementsOffset,
		final long                          elementsLength
	)
	{
		AbstractArrayStorage.set(
			this.data,
			this.size,
			Jadoth.checkArrayRange(offset),
			elements,
			elementsOffset,
			elementsLength
		);

		return this;
	}

	@Override
	public final BulkList<E> fill(final long offset, final long length, final E element)
	{
		AbstractArrayStorage.fill(
			this.data,
			this.size,
			Jadoth.checkArrayRange(offset),
			Jadoth.checkArrayRange(length),
			element
		);

		return this;
	}

	// sorting //

	@Override
	public final BulkList<E> sort(final Comparator<? super E> comparator)
	{
		JadothSort.mergesort(this.data, 0, this.size, comparator);
		return this;
	}

	// replacing - single //

	@Override
	public final boolean replaceOne(final E element, final E replacement)
	{
		return AbstractArrayStorage.replaceOne(this.data, this.size, element, replacement);
	}

	@Override
	public final boolean replaceOne(final Predicate<? super E> predicate, final E substitute)
	{
		return AbstractArrayStorage.substituteOne(this.data, this.size, predicate, substitute);
	}

	// replacing - multiple //

	@Override
	public final long replace(final E element, final E replacement)
	{
		return AbstractArrayStorage.replace(this.data, this.size, element, replacement);
	}

	@Override
	public final long replace(final Predicate<? super E> predicate, final E substitute)
	{
		return AbstractArrayStorage.substitute(this.data, this.size, predicate, substitute);
	}

	// replacing - multiple all //

	@Override
	public final long replaceAll(final XGettingCollection<? extends E> elements, final E replacement)
	{
		return AbstractArrayStorage.replaceAll(this.data, this.size, elements, replacement, AbstractArrayCollection.<E>marker());
	}

	// replacing - mapped //

	@Override
	public final long substitute(final Function<E, E> mapper)
	{
		return AbstractArrayStorage.modify(this.data, this.size, mapper);
	}

	@Override
	public final long substitute(final Predicate<? super E> predicate, final Function<E, E> mapper)
	{
		return AbstractArrayStorage.modify(this.data, this.size, predicate, mapper);
	}



	///////////////////////////////////////////////////////////////////////////
	// capacity methods //
	/////////////////////

	@Override
	public final long currentCapacity()
	{
		return this.data.length;
	}

	@Override
	public final long maximumCapacity()
	{
		return Integer.MAX_VALUE;
	}

	@Override
	public final boolean isFull()
	{
		return this.size >= Integer.MAX_VALUE;
	}

	@Override
	public final long optimize()
	{
		final int requiredCapacity;
		if((requiredCapacity = JadothMath.pow2BoundMaxed(this.size)) != this.data.length)
		{
			System.arraycopy(this.data, 0, this.data = newArray(requiredCapacity), 0, this.size);
		}
		return this.data.length;
	}

	@Override
	public final BulkList<E> ensureFreeCapacity(final long requiredFreeCapacity)
	{
		// as opposed to ensureCapacity(size + requiredFreeCapacity), this subtraction is overflow-safe
		if(this.data.length - this.size >= requiredFreeCapacity)
		{
			return this; // already enough free capacity
		}

		// calculate new capacity
		final int newSize = Jadoth.to_int(this.size + requiredFreeCapacity);
		int newCapacity;
		if(JadothMath.isGreaterThanHighestPowerOf2Integer(newSize))
		{
			// JVM technical limit
			newCapacity = Integer.MAX_VALUE;
		}
		else
		{
			newCapacity = this.data.length;
			while(newCapacity < newSize)
			{
				newCapacity <<= 1;
			}
		}

		// rebuild storage
		final E[] data = newArray(newCapacity);
		System.arraycopy(this.data, 0, data, 0, this.size);
		this.data = data;
		return this;
	}

	@Override
	public final BulkList<E> ensureCapacity(final long minCapacity)
	{
		if(minCapacity > this.data.length)
		{
			this.data = newArray(pow2BoundMaxed(minCapacity), this.data, this.size);
		}
		return this;
	}



	///////////////////////////////////////////////////////////////////////////
	//   add methods    //
	/////////////////////

	@Override
	public final void accept(final E element)
	{
		this.internalAdd(element); // gets inlined, tests showed no performance difference.
	}

	@Override
	public final boolean add(final E element)
	{
		this.internalAdd(element); // gets inlined, tests showed no performance difference.
		return true;
	}

	@SafeVarargs
	@Override
	public final BulkList<E> addAll(final E... elements)
	{
		this.ensureFreeCapacity(elements.length); // increaseCapacity
		System.arraycopy(elements, 0, this.data, this.size, elements.length);
		this.size += elements.length;
		return this;
	}

	@Override
	public final BulkList<E> addAll(final E[] elements, final int offset, final int length)
	{
		if(length >= 0)
		{
			this.ensureFreeCapacity(length); // increaseCapacity
			System.arraycopy(elements, offset, this.data, this.size, length); // automatic bounds checks
			this.size += length;
		}
		else
		{
			final int bound;
			if((bound = length + length) < -1)
			{
				throw new ArrayIndexOutOfBoundsException(bound + 1);
			}
			this.ensureFreeCapacity(-length); // increaseCapacity
			final Object[] data = this.data;
			int size = this.size;
			for(int i = length; i > bound; i--)
			{
				data[size++] = elements[i];
			}
			this.size = size;
		}
		return this;
	}

	@Override
	public final BulkList<E> addAll(final XGettingCollection<? extends E> elements)
	{
		if(elements instanceof AbstractSimpleArrayCollection)
		{
			return this.addAll(
				AbstractSimpleArrayCollection.internalGetStorageArray((AbstractSimpleArrayCollection<?>)elements),
				0,
				Jadoth.to_int(elements.size())
			);
		}
		return elements.iterate(this);
	}

	@Override
	public final boolean nullAdd()
	{
		if(this.size >= this.data.length)
		{
			if(this.size >= Integer.MAX_VALUE)
			{
				throw new CapacityExceededException();
			}
			System.arraycopy(this.data, 0, this.data = newArray((int)(this.data.length * 2.0f)), 0, this.size);
		}
		this.size++; // as overhang array elements are guaranteed to be null, the array setting can be spared
		return true;
	}



	///////////////////////////////////////////////////////////////////////////
	//   put methods    //
	/////////////////////

	@Override
	public final boolean nullPut()
	{
		return this.nullAdd();
	}

	@Override
	public final boolean put(final E element)
	{
		this.internalAdd(element); // gets inlined, tests showed no performance difference.
		return true;
	}

	@SafeVarargs
	@Override
	public final BulkList<E> putAll(final E... elements)
	{
		return this.addAll(elements);
	}

	@Override
	public final BulkList<E> putAll(final E[] elements, final int offset, final int length)
	{
		return this.addAll(elements, offset, length);
	}

	@Override
	public final BulkList<E> putAll(final XGettingCollection<? extends E> elements)
	{
		return elements.iterate(this);
	}



	///////////////////////////////////////////////////////////////////////////
	// prepend methods //
	////////////////////

	@Override
	public final boolean prepend(final E element)
	{
		if(this.size >= this.data.length)
		{
			if(this.size >= Integer.MAX_VALUE)
			{
				throw new CapacityExceededException();
			}
			System.arraycopy(this.data, 0, this.data = newArray((int)(this.data.length * 2.0f)), 1, this.size);
		}
		else
		{
			System.arraycopy(this.data, 0, this.data, 1, this.size); // ignore size == 0 corner case
		}
		this.data[0] = element;
		this.size++;
		return true;
	}

	@SafeVarargs
	@Override
	public final BulkList<E> prependAll(final E... elements)
	{
		this.internalInputArray(0, elements, elements.length);
		return this;
	}

	@Override
	public final BulkList<E> prependAll(final E[] elements, final int offset, final int length)
	{
		this.internalInputArray(0, elements, offset, length);
		return this;
	}

	@Override
	public final BulkList<E> prependAll(final XGettingCollection<? extends E> elements)
	{
		this.insertAll(0, elements);
		return this;
	}

	@Override
	public final boolean nullPrepend()
	{
		if(this.size >= this.data.length)
		{
			if(this.size >= Integer.MAX_VALUE)
			{
				throw new CapacityExceededException();
			}
			System.arraycopy(this.data, 0, this.data = newArray((int)(this.data.length * 2.0f)), 0, this.size);
		}
		else
		{
			System.arraycopy(this.data, 0, this.data, 1, this.size); // ignore size == 0 corner case
		}
		this.data[0] = null;
		this.size++;
		return true;
	}



	///////////////////////////////////////////////////////////////////////////
	// preput methods  //
	////////////////////

	@Override
	public final boolean preput(final E element)
	{
		if(this.size >= this.data.length)
		{
			if(this.size >= Integer.MAX_VALUE)
			{
				throw new CapacityExceededException();
			}
			System.arraycopy(this.data, 0, this.data = newArray((int)(this.data.length * 2.0f)), 0, this.size);
		}
		else
		{
			System.arraycopy(this.data, 0, this.data, 1, this.size); // ignore size == 0 corner case
		}
		this.data[0] = element;
		this.size++;
		return true;
	}

	@SafeVarargs
	@Override
	public final BulkList<E> preputAll(final E... elements)
	{
		this.internalInputArray(0, elements, elements.length);
		return this;
	}

	@Override
	public final BulkList<E> preputAll(final E[] elements, final int offset, final int length)
	{
		this.internalInputArray(0, elements, offset, length);
		return this;
	}

	@Override
	public final BulkList<E> preputAll(final XGettingCollection<? extends E> elements)
	{
		this.inputAll(0, elements);
		return this;
	}

	@Override
	public final boolean nullPreput()
	{
		if(this.size >= this.data.length)
		{
			if(this.size >= Integer.MAX_VALUE)
			{
				throw new CapacityExceededException();
			}
			System.arraycopy(this.data, 0, this.data = newArray((int)(this.data.length * 2.0f)), 0, this.size);
		}
		else
		{
			System.arraycopy(this.data, 0, this.data, 1, this.size); // ignore size == 0 corner case
		}
		this.data[0] = null;
		this.size++;
		return true;
	}



	///////////////////////////////////////////////////////////////////////////
	//  insert methods  //
	/////////////////////

	@Override
	public final boolean insert(final long index, final E element)
	{
		if(index >= this.size || index < 0)
		{
			if(index == this.size)
			{
				if(this.size >= this.data.length)
				{
					this.checkSizeIncreasable();
					System.arraycopy(this.data, 0, this.data = newArray((int)(this.data.length * 2.0f)), 0, this.size);
				}
				this.data[this.size++] = element;
				return true;
			}
			throw new IndexExceededException(this.size, index);
		}

		if(this.size >= this.data.length)
		{
			this.checkSizeIncreasable();
			final Object[] oldData = this.data;
			System.arraycopy(this.data, 0, this.data = newArray((int)(this.data.length * 2.0f)), 0, (int)index);
			System.arraycopy(oldData, (int)index, this.data, (int)index + 1, this.size - (int)index);
		}
		else
		{
			System.arraycopy(this.data, (int)index, this.data, (int)index + 1, this.size - (int)index);
		}

		this.data[(int)index] = element;
		this.size++;

		return true;
	}

	@SafeVarargs
	@Override
	public final long insertAll(final long index, final E... elements) throws IndexOutOfBoundsException
	{
		if(index >= this.size || index < 0)
		{
			if(index == this.size)
			{
				return this.internalCountingAddAll(elements);
			}
			throw new IndexExceededException(this.size, index);
		}

		return this.internalInputArray((int)index, elements, elements.length);
	}

	@Override
	public final long insertAll(final long index, final E[] elements, final int offset, final int length)
	{
		if(index >= this.size || index < 0)
		{
			if(index == this.size)
			{
				return this.internalCountingAddAll(elements, offset, length);
			}
			throw new IndexExceededException(this.size, index);
		}

		return this.internalInputArray((int)index, elements, offset, length);
	}

	@Override
	public final long insertAll(final long index, final XGettingCollection<? extends E> elements)
	{
		if(index >= this.size || index < 0)
		{
			if(index == this.size)
			{
				return this.internalCountingAddAll(elements);
			}
			throw new IndexExceededException(this.size, index);
		}

		@SuppressWarnings("unchecked")
		final Object[] elementsToAdd = elements instanceof AbstractSimpleArrayCollection
			? ((AbstractSimpleArrayCollection<? extends E>)elements).internalGetStorageArray()
			: elements.toArray() // anything else is probably not worth the hassle
		;

		return this.internalInputArray((int)index, elementsToAdd, elementsToAdd.length);
	}

	@Override
	public final boolean nullInsert(final long index)
	{
		return this.insert(0, (E)null);
	}



	///////////////////////////////////////////////////////////////////////////
	//  input methods   //
	/////////////////////

	@Override
	public final boolean input(final long index, final E element)
	{
		if(index >= this.size || index < 0)
		{
			if(index == this.size)
			{
				if(this.size >= this.data.length)
				{
					this.checkSizeIncreasable();
					System.arraycopy(this.data, 0, this.data = newArray((int)(this.data.length * 2.0f)), 0, this.size);
				}
				this.data[this.size++] = element;
				return true;
			}
			throw new IndexExceededException(this.size, index);
		}

		if(this.size >= this.data.length)
		{
			this.checkSizeIncreasable();
			final E[] oldData = this.data;
			System.arraycopy(this.data, 0, this.data = newArray((int)(this.data.length * 2.0f)), 0, (int)index);
			System.arraycopy(oldData, (int)index, this.data, (int)index + 1, this.size - (int)index);
		}
		else
		{
			System.arraycopy(this.data, (int)index, this.data, (int)index + 1, this.size - (int)index);
		}
		this.data[(int)index] = element;
		this.size++;
		return true;
	}

	@SafeVarargs
	@Override
	public final long inputAll(final long index, final E... elements) throws IndexOutOfBoundsException
	{
		if(index >= this.size || index < 0)
		{
			if(index == this.size)
			{
				return this.internalCountingPutAll(elements);
			}
			throw new IndexExceededException(this.size, index);
		}
		return this.internalInputArray((int)index, elements, elements.length);
	}

	@Override
	public final long inputAll(final long index, final E[] elements, final int offset, final int length)
	{
		if(index >= this.size || index < 0)
		{
			if(index == this.size)
			{
				return this.internalCountingPutAll(elements, offset, length);
			}
			throw new IndexExceededException(this.size, index);
		}
		return this.internalInputArray((int)index, elements, offset, length);
	}

	@Override
	public final long inputAll(final long index, final XGettingCollection<? extends E> elements)
	{
		if(index >= this.size || index < 0)
		{
			if(index == this.size)
			{
				return this.internalCountingPutAll(elements);
			}
			throw new IndexExceededException(this.size, index);
		}
		final Object[] elementsToAdd = elements instanceof AbstractSimpleArrayCollection
			? ((AbstractSimpleArrayCollection<?>)elements).internalGetStorageArray()
			: elements.toArray() // anything else is probably not worth the hassle
		;
		return this.internalInputArray((int)index, elementsToAdd, elementsToAdd.length);
	}

	@Override
	public final boolean nullInput(final long index)
	{
		return this.input(0, (E)null);
	}



	///////////////////////////////////////////////////////////////////////////
	//  remove methods  //
	/////////////////////

	@Override
	public final void truncate()
	{
		this.size = 0;
		this.data = newArray(1);
	}

	@Override
	public final long consolidate()
	{
		return 0; // nothing to do here
	}

	// removing - single //

	@Override
	public final boolean removeOne(final E element)
	{
		if(AbstractArrayStorage.removeOne(this.data, this.size, element))
		{
			this.size--;
			return true;
		}
		return false;
	}

	@Override
	public final E retrieve(final E element)
	{
		final E removedElement;
		if((removedElement = AbstractArrayStorage.retrieve(
			this.data, this.size, element, AbstractArrayCollection.<E>marker())) != AbstractArrayCollection.<E>marker()
		)
		{
			this.size--;
			return removedElement;
		}
		return null;
	}

	@Override
	public final E retrieveBy(final Predicate<? super E> predicate)
	{
		final E e;
		if((e = AbstractArrayStorage.retrieve(
			this.data, this.size, predicate, AbstractArrayCollection.<E>marker())) != AbstractArrayCollection.<E>marker()
		)
		{
			this.size--;
			return e;
		}
		return null;
	}

	// removing - multiple //

	@Override
	public final long remove(final E element)
	{
		int removeCount;
		this.size -= removeCount = removeAllFromArray(this.data, 0, this.size, element);
		return removeCount;
	}

	@Override
	public final long nullRemove()
	{
		final int removeCount;
		this.size -= removeCount = JadothArrays.removeAllFromArray(this.data, 0, this.size, null);
		return removeCount;
	}

	@Override
	public final E removeAt(final long index) throws IndexOutOfBoundsException, ArrayIndexOutOfBoundsException
	{
		if(index >= this.size)
		{
			throw new IndexExceededException(this.size, index);
		}

		final E oldValue = this.data[(int)index];

		final int moveCount;
		if((moveCount = this.size - 1 - (int)index) > 0)
		{
			System.arraycopy(this.data, (int)index + 1, this.data, (int)index, moveCount);
		}
		this.data[--this.size] = null;

		return oldValue;
	}

	// reducing //

	@Override
	public final long removeBy(final Predicate<? super E> predicate)
	{
		final int removeCount;
		this.size -= removeCount = AbstractArrayStorage.reduce(
			this.data, this.size, predicate, AbstractArrayCollection.<E>marker()
		);
		return removeCount;
	}

	// retaining //

	@SuppressWarnings("unchecked")
	@Override
	public final long retainAll(final XGettingCollection<? extends E> elements)
	{
		final int removeCount;
		this.size -= removeCount = AbstractArrayStorage.retainAll(
			this.data, this.size, (XGettingCollection<E>)elements, AbstractArrayCollection.<E>marker()
		);
		return removeCount;
	}

	// processing //

	@Override
	public final <P extends Consumer<? super E>> P process(final P procedure)
	{
		this.size -= AbstractArrayStorage.process(this.data, this.size, procedure, AbstractArrayCollection.<E>marker());
		return procedure;
	}

	// moving //

	@Override
	public final <C extends Consumer<? super E>> C moveTo(final C target, final Predicate<? super E> predicate)
	{
		this.size -= AbstractArrayStorage.moveTo(this.data, this.size, target, predicate, AbstractArrayCollection.<E>marker());
		return target;
	}

	@Override
	public final <C extends Consumer<? super E>> C moveSelection(final C target, final long... indices)
	{
		this.size -= AbstractArrayStorage.moveSelection(this.data, this.size, indices, target, AbstractArrayCollection.<E>marker());
		return target;
	}

	// removing - multiple all //

	@Override
	public final long removeAll(final XGettingCollection<? extends E> elements)
	{
		final int removed;
		this.size -= removed = removeAllFromArray(elements, this.data, 0, this.size);
		return removed;
	}

	// removing - duplicates //

	@Override
	public final long removeDuplicates(final Equalator<? super E> equalator)
	{
		final int removeCount;
		this.size -= removeCount = AbstractArrayStorage.removeDuplicates(
			this.data, this.size, equalator, AbstractArrayCollection.<E>marker()
		);
		return removeCount;
	}

	@Override
	public final long removeDuplicates()
	{
		final int removeCount;
		this.size -= removeCount = AbstractArrayStorage.removeDuplicates(
			this.data, this.size, AbstractArrayCollection.<E>marker()
		);
		return removeCount;
	}

	// removing - indexed //

	@Override
	public final E fetch()
	{
		final E element = this.data[0];
		System.arraycopy(this.data, 1, this.data, 0, --this.size);
		this.data[this.size] = null;
		return element;
	}

	@Override
	public final E pop()
	{
		final E element = this.data[this.size - 1]; // get element and provoke index exception
		this.data[--this.size] = null; // update state
		return element;
	}

	@Override
	public final E pinch()
	{
		if(this.size == 0)
		{
			return null;
		}
		final E element = this.data[0];
		System.arraycopy(this.data, 1, this.data, 0, --this.size);
		this.data[this.size] = null;
		return element;
	}

	@Override
	public final E pick()
	{
		if(this.size == 0)
		{
			return null;
		}
		final E element = this.data[--this.size];
		this.data[this.size] = null;
		return element;
	}

	@Override
	public final long removeSelection(final long[] indices)
	{
		final int removeCount;
		this.size -= removeCount = AbstractArrayStorage.removeSelection(
			this.data, this.size, indices, AbstractArrayCollection.<E>marker()
		);
		return removeCount;
	}

	@Override
	public final BulkList<E> removeRange(final long startIndex, final long length)
	{
		this.size -= AbstractArrayStorage.removeRange(
			this.data,
			this.size,
			Jadoth.checkArrayRange(startIndex),
			Jadoth.checkArrayRange(length)
		);

		return this;
	}

	@Override
	public final BulkList<E> retainRange(final long startIndex, final long length)
	{
		AbstractArrayStorage.retainRange(
			this.data,
			this.size,
			Jadoth.checkArrayRange(startIndex),
			Jadoth.checkArrayRange(length)
		);
		this.size = (int)length;

		return this;
	}

	@Override
	public final SubList<E> range(final long fromIndex, final long toIndex)
	{
		// range check is done in constructor
		return new SubList<>(this, fromIndex, toIndex);
	}

	@Override
	public final boolean isEmpty()
	{
		return this.size == 0;
	}

	@Override
	public final Iterator<E> iterator()
	{
		return new GenericListIterator<>(this);
	}

	@Override
	public final ListIterator<E> listIterator()
	{
		return new GenericListIterator<>(this);
	}

	@Override
	public final ListIterator<E> listIterator(final long index) throws IndexBoundsException
	{
		validateIndex(this.size, index);
		return new GenericListIterator<>(this, (int)index);
	}

	@Override
	public final E at(final long index) throws IndexBoundsException
	{
		validateIndex(this.size, index);
		return this.data[(int)index];
	}

	@Override
	public final boolean set(final long index, final E element) throws IndexBoundsException
	{
		validateIndex(this.size, index);

		this.data[(int)index] = element;

		return false;
	}

	@Override
	public final E setGet(final long index, final E element) throws IndexBoundsException
	{
		validateIndex(this.size, index);

		final E old = this.data[(int)index];
		this.data[(int)index] = element;

		return old;
	}

	@Override
	public final long size()
	{
		return this.size;
	}

	@Override
	public final String toString()
	{
		return AbstractArrayStorage.toString(this.data, this.size);
	}

	@Override
	public final Object[] toArray()
	{
		final Object[] array = new Object[this.size];
		System.arraycopy(this.data, 0, array, 0, this.size);
		return array;
	}

	@Override
	public final void clear()
	{
		final Object[] data = this.data;
		for(int i = this.size; i-- > 0;)
		{
			data[i] = null;
		}
		this.size = 0;
	}

	@Deprecated
	@Override
	public final boolean equals(final Object o)
	{
		//trivial escape conditions
		if(o == this)
		{
			return true;
		}
		if(o == null || !(o instanceof List<?>))
		{
			return false;
		}

		final List<?> list = (List<?>)o;
		if(this.size != list.size())
		{
			return false; //lists can only be equal if they have the same length
		}

		final Object[] data = this.data;
		int i = 0;
		for(final Object e2 : list)
		{
			//use iterator for passed list as it could be a non-random-access list
			final Object e1 = data[i++];
			if(e1 == null)
			{
				//null-handling escape conditions
				if(e2 != null)
				{
					return false;
				}
				continue;
			}
			if(!e1.equals(e2))
			{
				return false;
			}
		}
		return true; //no un-equal element found, so lists must be equal
	}

	@Deprecated
	@Override
	public final int hashCode()
	{
		return JadothArrays.arrayHashCode(this.data, this.size);
	}



	@Override
	public final OldBulkList<E> old()
	{
		return new OldBulkList<>(this);
	}

	public static final class OldBulkList<E> extends AbstractBridgeXList<E>
	{
		OldBulkList(final BulkList<E> list)
		{
			super(list);
		}

		@Override
		public final BulkList<E> parent()
		{
			return (BulkList<E>)super.parent();
		}

	}



	public static final class Creator<E> implements XList.Creator<E>
	{
		private final int initialCapacity;

		public Creator(final int initialCapacity)
		{
			super();
			this.initialCapacity = JadothMath.pow2BoundMaxed(initialCapacity);
		}

		public final int getInitialCapacity()
		{
			return this.initialCapacity;
		}

		@Override
		public final BulkList<E> newInstance()
		{
			return new BulkList<>(this.initialCapacity);
		}

	}

	public static final class Supplier<K, E> implements Function<K, BulkList<E>>
	{
		private final int initialCapacity;

		public Supplier(final int initialCapacity)
		{
			super();
			this.initialCapacity = JadothMath.pow2BoundMaxed(initialCapacity);
		}

		public final int getInitialCapacity()
		{
			return this.initialCapacity;
		}

		@Override
		public final BulkList<E> apply(final K key)
		{
			return new BulkList<>(this.initialCapacity);
		}

	}

	@Override
	public final Aggregator<E, BulkList<E>> collector()
	{
		return new Aggregator<E, BulkList<E>>()
		{
			@Override
			public void accept(final E element)
			{
				BulkList.this.add(element);
			}

			@Override
			public BulkList<E> yield()
			{
				return BulkList.this;
			}
		};
	}

}
