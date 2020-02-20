package one.microstream.storage.types;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

import one.microstream.io.XIO;
import one.microstream.storage.exceptions.StorageException;
import one.microstream.storage.exceptions.StorageExceptionIo;


/**
 * Function type that encapsulates handling of all writing accesses to persistent data, including copying,
 * truncation, deletion.
 *
 * @author TM
 */
public interface StorageFileWriter
{
	public static long validateIoByteCount(final long specifiedByteCount, final long actualByteCount)
	{
		if(specifiedByteCount == actualByteCount)
		{
			return actualByteCount; // validation successful
		}

		// (28.06.2013 TM)EXCP: proper exception
		throw new StorageException(
			"Inconsistent IO operation: actual byte count " + actualByteCount
			+ " does not match the specified byte count if  " + specifiedByteCount + "."
		);
	}
	
	public default long write(final StorageLockedFile file, final ByteBuffer[] byteBuffers)
	{
		try
		{
			return XIO.appendAllGuaranteed(file.fileChannel(), byteBuffers);
		}
		catch(final IOException e)
		{
			// (01.10.2014 TM)EXCP: proper exception
			throw new StorageException(e);
		}
	}

	public default long copy(
		final StorageLockedFile sourceFile,
		final StorageLockedFile targetfile
	)
	{
		return this.copyFilePart(sourceFile, 0, sourceFile.length(), targetfile);
	}

	public default long copyFilePart(
		final StorageLockedFile sourceFile  ,
		final long              sourceOffset,
		final long              length      ,
		final StorageLockedFile targetfile
	)
	{
//		DEBUGStorage.println("storage copy file range");

		try
		{
			final long byteCount = sourceFile.fileChannel().transferTo(sourceOffset, length, targetfile.fileChannel());
			targetfile.fileChannel().force(false);
			
			return validateIoByteCount(length, byteCount);
		}
		catch(final IOException e)
		{
			throw new StorageException(e); // (01.10.2014 TM)EXCP: proper exception
		}
	}
	
	public default long writeStore(
		final StorageDataFile<?> targetFile ,
		final ByteBuffer[]       byteBuffers
	)
	{
		return this.write(targetFile, byteBuffers);
	}
	
	/**
	 * Logically the same as a store, but technically the same as a transfer with an external source file.
	 * 
	 */
	public default long writeImport(
		final StorageLockedFile  sourceFile  ,
		final long               sourceOffset,
		final long               copyLength  ,
		final StorageDataFile<?> targetFile
	)
	{
		return this.copyFilePart(sourceFile, sourceOffset, copyLength, targetFile);
	}
	
	public default long writeTransfer(
		final StorageDataFile<?> sourceFile  ,
		final long               sourceOffset,
		final long               copyLength  ,
		final StorageDataFile<?> targetFile
	)
	{
		return this.copyFilePart(sourceFile, sourceOffset, copyLength, targetFile);
	}
	
	public default long writeTransactionEntryCreate(
		final StorageInventoryFile transactionFile,
		final ByteBuffer[]         byteBuffers    ,
		final StorageDataFile<?>   dataFile
	)
	{
		return this.write(transactionFile, byteBuffers);
	}
	
	public default long writeTransactionEntryStore(
		final StorageInventoryFile transactionFile,
		final ByteBuffer[]         byteBuffers    ,
		final StorageDataFile<?>   dataFile       ,
		final long                 dataFileOffset ,
		final long                 storeLength
	)
	{
		return this.write(transactionFile, byteBuffers);
	}
	
	public default long writeTransactionEntryTransfer(
		final StorageInventoryFile transactionFile,
		final ByteBuffer[]             byteBuffers    ,
		final StorageDataFile<?>       dataFile       ,
		final long                     dataFileOffset ,
		final long                     storeLength
	)
	{
		return this.write(transactionFile, byteBuffers);
	}
	
	public default long writeTransactionEntryDelete(
		final StorageInventoryFile transactionFile,
		final ByteBuffer[]             byteBuffers    ,
		final StorageDataFile<?>       dataFile
	)
	{
		return this.write(transactionFile, byteBuffers);
	}
	
	public default long writeTransactionEntryTruncate(
		final StorageInventoryFile transactionFile,
		final ByteBuffer[]             byteBuffers    ,
		final StorageInventoryFile     file           ,
		final long                     newFileLength
	)
	{
		return this.write(transactionFile, byteBuffers);
	}

	public default void truncate(
		final StorageInventoryFile file               ,
		final long                 newLength          ,
		final StorageFileProvider  storageFileProvider
	)
	{
		truncateFile(file, newLength, storageFileProvider);
	}
	
	public static void truncateFile(
		final StorageNumberedFile file               ,
		final long                newLength          ,
		final StorageFileProvider storageFileProvider
	)
	{
//		DEBUGStorage.println("storage file truncation");
		final StorageNumberedFile truncationTargetFile = storageFileProvider.provideTruncationBackupTargetFile(
			file,
			newLength
		);
		if(truncationTargetFile != null)
		{
			createFileFullCopy(file, truncationTargetFile);
		}

		try
		{
			file.fileChannel().truncate(newLength);
		}
		catch(final IOException e)
		{
			throw new StorageException(e); // (01.10.2014 TM)EXCP: proper exception
		}
	}

	public default void delete(
		final StorageInventoryFile file               ,
		final StorageFileProvider  storageFileProvider
	)
	{
		deleteFile(file, storageFileProvider);
	}
	
	public static void deleteFile(
		final StorageNumberedFile file               ,
		final StorageFileProvider storageFileProvider
	)
	{
//		DEBUGStorage.println("storage file deletion");

		if(rescueFromDeletion(file, storageFileProvider))
		{
			return;
		}
		
		if(file.delete())
		{
			return;
		}
		
		throw new StorageException("Could not delete file " + file); // (02.10.2014 TM)EXCP: proper exception
	}
	

	public static void createFileFullCopy(
		final StorageNumberedFile sourceFile,
		final StorageNumberedFile targetFile
	)
	{
		try
		{
			final Path source = Paths.get(sourceFile.identifier());
			final Path target = Paths.get(targetFile.identifier());
			if(!Files.exists(source))
			{
				throw new IOException("Copying source file does not exist: " + sourceFile.identifier());
			}
			if(Files.exists(target))
			{
				throw new IOException("Copying target already exist: " + targetFile.identifier());
			}
			
			XIO.copyFile(source, target, StandardOpenOption.CREATE_NEW);
			
			// (20.02.2020 TM)NOTE: Files#copy is bugged as it recognizes the process's file locks as foreign (rofl).
//			Files.copy(source, target);
		}
		catch(final Exception e)
		{
			throw new StorageExceptionIo(e); // (04.03.2015 TM)EXCP: proper exception
		}
	}
	
	public static boolean rescueFromDeletion(
		final StorageNumberedFile file               ,
		final StorageFileProvider storageFileProvider
	)
	{
		final StorageNumberedFile deletionTargetFile = storageFileProvider.provideDeletionTargetFile(file);
		if(deletionTargetFile == null)
		{
			return false;
		}
		
		try
		{
			final Path source = Paths.get(file.identifier());
			final Path target = Paths.get(deletionTargetFile.identifier());
			Files.move(source, target);
		}
		catch(final Exception e)
		{
			throw new StorageExceptionIo(e); // (04.03.2015 TM)EXCP: proper exception
		}
		
		return true;
	}
	
	public default void flush(final StorageLockedFile targetfile)
	{
		try
		{
			targetfile.fileChannel().force(false);
		}
		catch(final IOException e)
		{
			throw new StorageException(e); // (01.10.2014 TM)EXCP: proper exception
		}
	}

	public final class Default implements StorageFileWriter
	{
		// since default methods, interfaces should be directly instantiable :(
	}
	
	

	@FunctionalInterface
	public interface Provider
	{
		public StorageFileWriter provideWriter();
		
		public default StorageFileWriter provideWriter(final int channelIndex)
		{
			return this.provideWriter();
		}

		public final class Default implements StorageFileWriter.Provider
		{
			@Override
			public StorageFileWriter provideWriter()
			{
				return new StorageFileWriter.Default();
			}
		}

	}
	
}
