package nachos.userprog;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;
import nachos.userprog.UserKernel.InadequatePagesException;

import java.io.EOFException;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;

/**
 * Encapsulates the state of a user process that is not contained in its
 * user thread (or threads). This includes its address translation state, a
 * file table, and information about the program being executed.
 *
 * <p>
 * This class is extended by other classes to support additional functionality
 * (such as additional syscalls).
 *
 * @see	nachos.vm.VMProcess
 * @see	nachos.network.NetProcess
 */
public class UserProcess {
	/**
	 * Allocate a new process.
	 */
	public UserProcess() {
		sharedStateLock.acquire();
		PID = nextPID++;
		runningProcesses++;
		sharedStateLock.release();

		// stdin/stdout
		fileTable[0] = UserKernel.console.openForReading();
		FileRef.referenceFile(fileTable[0].getName());
		fileTable[1] = UserKernel.console.openForWriting();
		FileRef.referenceFile(fileTable[1].getName());

		// Exit/Join syncronization
		waitingToJoin = new Condition(joinLock);
	}

	/**
	 * Allocate and return a new process of the correct class. The class name
	 * is specified by the <tt>nachos.conf</tt> key
	 * <tt>Kernel.processClassName</tt>.
	 *
	 * @return	a new process of the correct class.
	 */
	public static UserProcess newUserProcess() {
		return (UserProcess)Lib.constructObject(Machine.getProcessClassName());
	}

	/**
	 * Execute the specified program with the specified arguments. Attempts to
	 * load the program, and then forks a thread to run it.
	 *
	 * @param	name	the name of the file containing the executable.
	 * @param	args	the arguments to pass to the executable.
	 * @return	<tt>true</tt> if the program was successfully executed.
	 */
	public boolean execute(String name, String[] args) {
		if (!load(name, args))
			return false;

		new UThread(this).setName(name).fork();

		return true;
	}

	/**
	 * Save the state of this process in preparation for a context switch.
	 * Called by <tt>UThread.saveState()</tt>.
	 */
	public void saveState() {
	}

	/**
	 * Restore the state of this process after a context switch. Called by
	 * <tt>UThread.restoreState()</tt>.
	 */
	public void restoreState() {
		Machine.processor().setPageTable(pageTable);
	}

	protected boolean validAddress(int vaddr) {
		int vpn = Processor.pageFromAddress(vaddr);
		return vpn < numPages && vpn >= 0;
	}

	/**
	 * Read a null-terminated string from this process's virtual memory. Read
	 * at most <tt>maxLength + 1</tt> bytes from the specified address, search
	 * for the null terminator, and convert it to a <tt>java.lang.String</tt>,
	 * without including the null terminator. If no null terminator is found,
	 * returns <tt>null</tt>.
	 *
	 * @param	vaddr	the starting virtual address of the null-terminated
	 *			string.
	 * @param	maxLength	the maximum number of characters in the string,
	 *				not including the null terminator.
	 * @return	the string read, or <tt>null</tt> if no null terminator was
	 *		found.
	 */
	public String readVirtualMemoryString(int vaddr, int maxLength) {
		Lib.assertTrue(maxLength >= 0);

		byte[] bytes = new byte[maxLength+1];

		int bytesRead = readVirtualMemory(vaddr, bytes);

		for (int length=0; length<bytesRead; length++) {
			if (bytes[length] == 0)
				return new String(bytes, 0, length);
		}

		return null;
	}

	/**
	 * Transfer data from this process's virtual memory to all of the specified
	 * array. Same as <tt>readVirtualMemory(vaddr, data, 0, data.length)</tt>.
	 *
	 * @param	vaddr	the first byte of virtual memory to read.
	 * @param	data	the array where the data will be stored.
	 * @return	the number of bytes successfully transferred.
	 */
	public int readVirtualMemory(int vaddr, byte[] data) {
		return readVirtualMemory(vaddr, data, 0, data.length);
	}



	/**
	 * Transfer data from this process's virtual memory to the specified array.
	 * This method handles address translation details. This method must
	 * <i>not</i> destroy the current process if an error occurs, but instead
	 * should return the number of bytes successfully copied (or zero if no
	 * data could be copied).
	 *
	 * @param	vaddr	the first byte of virtual memory to read.
	 * @param	data	the array where the data will be stored.
	 * @param	offset	the first byte to write in the array.
	 * @param	length	the number of bytes to transfer from virtual memory to
	 *			the array.
	 * @return	the number of bytes successfully transferred.
	 */
	public int readVirtualMemory(int vaddr, byte[] data, int offset,
			int length) {
		Lib.assertTrue(offset >= 0 && length >= 0 && offset+length <= data.length && memoryAccessLock != null);
		if (!validAddress(vaddr)) {
			return 0;
		}
		else {
			Collection<MemoryAccess> memoryAccesses = createMemoryAccesses(vaddr, data, offset, length, AccessType.READ);

			int bytesRead = 0, temp;

			memoryAccessLock.acquire();
			for (MemoryAccess ma : memoryAccesses) {
				temp = ma.executeAccess();

				if (temp == 0) break;
				else bytesRead += temp;
			}
			memoryAccessLock.release();
			return bytesRead;
		}
	}

	/**
	 * Transfer all data from the specified array to this process's virtual
	 * memory.
	 * Same as <tt>writeVirtualMemory(vaddr, data, 0, data.length)</tt>.
	 *
	 * @param	vaddr	the first byte of virtual memory to write.
	 * @param	data	the array containing the data to transfer.
	 * @return	the number of bytes successfully transferred.
	 */
	public int writeVirtualMemory(int vaddr, byte[] data) {
		return writeVirtualMemory(vaddr, data, 0, data.length);
	}

	/**
	 * Transfer data from the specified array to this process's virtual memory.
	 * This method handles address translation details. This method must
	 * <i>not</i> destroy the current process if an error occurs, but instead
	 * should return the number of bytes successfully copied (or zero if no
	 * data could be copied).
	 *
	 * @param	vaddr	the first byte of virtual memory to write.
	 * @param	data	the array containing the data to transfer.
	 * @param	offset	the first byte to transfer from the array.
	 * @param	length	the number of bytes to transfer from the array to
	 *			virtual memory.
	 * @return	the number of bytes successfully transferred.
	 */
	public int writeVirtualMemory(int vaddr, byte[] data, int offset, int length) {
		Lib.assertTrue(offset >= 0 && length >= 0 && offset + length <= data.length && memoryAccessLock != null);
		if (!validAddress(vaddr)) {
			return 0;
		} 
		else {
			Collection<MemoryAccess> memoryAccesses = createMemoryAccesses(vaddr, data, offset, length, AccessType.WRITE);

			int bytesWritten = 0, temp;
			memoryAccessLock.acquire();
			for (MemoryAccess ma : memoryAccesses) {
				temp = ma.executeAccess();
				if (temp == 0) break;
				else bytesWritten += temp;
			}
			memoryAccessLock.release();

			return bytesWritten;
		}
	}

	private Collection<MemoryAccess> createMemoryAccesses(int vaddr, byte[] data, int offset, int length, AccessType accessType) {
		LinkedList<MemoryAccess> returnList = new LinkedList<MemoryAccess>();

		while (length > 0) {
			int vpn = Processor.pageFromAddress(vaddr);

			int potentialPageAccess = Processor.pageSize - Processor.offsetFromAddress(vaddr);
			int accessSize = length < potentialPageAccess ? length : potentialPageAccess;

			returnList.add(new MemoryAccess(accessType, data, vpn, offset, Processor.offsetFromAddress(vaddr), accessSize));
			length -= accessSize;
			vaddr += accessSize;
			offset += accessSize;
		}

		return returnList;
	}

	protected class MemoryAccess {
		protected MemoryAccess(AccessType at, byte[] d, int _vpn, int dStart, int pStart, int len) {
			accessType = at;
			data = d;
			vpn = _vpn;
			dataStart = dStart;
			pageStart = pStart;
			length = len;
		}

		public int executeAccess() {
			if (translationEntry == null)translationEntry = pageTable[vpn];
			if (translationEntry.valid) {
				if (accessType == AccessType.READ) {
					System.arraycopy(Machine.processor().getMemory(), pageStart + (Processor.pageSize * translationEntry.ppn), data, dataStart, length);
					translationEntry.used = true;
					return length;
				} else if (!translationEntry.readOnly && accessType == AccessType.WRITE) {
					System.arraycopy(data, dataStart, Machine.processor().getMemory(), pageStart + (Processor.pageSize * translationEntry.ppn), length);
					translationEntry.used = translationEntry.dirty = true;
					return length;
				}
			}

			return 0;
		}

		protected byte[] data;

		protected AccessType accessType;

		protected TranslationEntry translationEntry;

		protected int dataStart;

		protected int pageStart;

		protected int length;

		protected int vpn;
	}

	protected static enum AccessType {
		READ, WRITE
	};

	/**
	 * Load the executable with the specified name into this process, and
	 * prepare to pass it the specified arguments. Opens the executable, reads
	 * its header information, and copies sections and arguments into this
	 * process's virtual memory.
	 *
	 * @param	name	the name of the file containing the executable.
	 * @param	args	the arguments to pass to the executable.
	 * @return	<tt>true</tt> if the executable was successfully loaded.
	 */
	private boolean load(String name, String[] args) {
		Lib.debug(dbgProcess, "UserProcess.load(\"" + name + "\")");

		OpenFile executable = ThreadedKernel.fileSystem.open(name, false);
		if (executable == null) {
			Lib.debug(dbgProcess, "\topen failed");
			return false;
		}

		try {
			coff = new Coff(executable);
		}
		catch (EOFException e) {
			executable.close();
			Lib.debug(dbgProcess, "\tcoff load failed");
			return false;
		}

		// make sure the sections are contiguous and start at page 0
		numPages = 0;
		for (int s = 0; s < coff.getNumSections(); s++) {
			CoffSection section = coff.getSection(s);
			if (section.getFirstVPN() != numPages) {
				coff.close();
				Lib.debug(dbgProcess, "\tfragmented executable");
				return false;
			}
			numPages += section.getLength();
		}

		// make sure the argv array will fit in one page
		byte[][] argv = new byte[args.length][];
		int argsSize = 0;
		for (int i=0; i<args.length; i++) {
			argv[i] = args[i].getBytes();
			// 4 bytes for argv[] pointer; then string plus one for null byte
			argsSize += 4 + argv[i].length + 1;
		}
		if (argsSize > pageSize) {
			coff.close();
			Lib.debug(dbgProcess, "\targuments too long");
			return false;
		}

		// program counter initially points at the program entry point
		initialPC = coff.getEntryPoint();	

		// next comes the stack; stack pointer initially points to top of it
		numPages += stackPages;
		initialSP = numPages*pageSize;

		// and finally reserve 1 page for arguments
		numPages++;

		if (!loadSections())
			return false;

		// store arguments in last page
		int entryOffset = (numPages-1)*pageSize;
		int stringOffset = entryOffset + args.length*4;

		this.argc = args.length;
		this.argv = entryOffset;

		loadArguments(entryOffset, stringOffset, argv);

		return true;
	}

	protected void loadArguments(int entryOffset, int stringOffset, byte[][] argv) {

		for (int i = 0; i < argv.length; i++) {
			byte[] stringOffsetBytes = Lib.bytesFromInt(stringOffset);
			Lib.assertTrue(writeVirtualMemory(entryOffset, stringOffsetBytes) == 4);
			entryOffset += 4;
			Lib.assertTrue(writeVirtualMemory(stringOffset, argv[i]) == argv[i].length);
			stringOffset += argv[i].length;
			Lib.assertTrue(writeVirtualMemory(stringOffset, new byte[] { 0 }) == 1);
			stringOffset += 1;
		}
	}

	/**
	 * Allocates memory for this process, and loads the COFF sections into
	 * memory. If this returns successfully, the process will definitely be
	 * run (this is the last step in process initialization that can fail).
	 *
	 * @return	<tt>true</tt> if the sections were successfully loaded.
	 */
	protected boolean loadSections() {
		try {
			pageTable = ((UserKernel) Kernel.kernel).acquirePages(numPages);

			for (int i = 0; i < pageTable.length; i++)
				pageTable[i].vpn = i;

			for (int sectionNumber = 0; sectionNumber < coff.getNumSections(); sectionNumber++) {
				CoffSection section = coff.getSection(sectionNumber);

				Lib.debug(dbgProcess, "\tinitializing " + section.getName() + " section (" + section.getLength() + " pages)");

				int firstVPN = section.getFirstVPN();
				for (int i = 0; i < section.getLength(); i++)
					section.loadPage(i, pageTable[i+firstVPN].ppn);
			}
		} catch (InadequatePagesException a) {
			coff.close();
			Lib.debug(dbgProcess, "\tinsufficient physical memory");
			return false;
		} catch (ClassCastException c) {
			Lib.assertNotReached("Error : instantiating a UserProcess without a UserKernel");
		}

		return true;
	}

	/**
	 * Release any resources allocated by <tt>loadSections()</tt>.
	 */
	protected void unloadSections() {
		try {
			((UserKernel)Kernel.kernel).releasePages(pageTable);
		} catch (ClassCastException c) {
			Lib.assertNotReached("Error : Kernel is not an instance of UserKernel");
		}
	}    

	/**
	 * Initialize the processor's registers in preparation for running the
	 * program loaded into this process. Set the PC register to point at the
	 * start function, set the stack pointer register to point at the top of
	 * the stack, set the A0 and A1 registers to argc and argv, respectively,
	 * and initialize all other registers to 0.
	 */
	public void initRegisters() {
		Processor processor = Machine.processor();

		// by default, everything's 0
		for (int i=0; i<processor.numUserRegisters; i++)
			processor.writeRegister(i, 0);

		// initialize PC and SP according
		processor.writeRegister(Processor.regPC, initialPC);
		processor.writeRegister(Processor.regSP, initialSP);

		// initialize the first two argument registers to argc and argv
		processor.writeRegister(Processor.regA0, argc);
		processor.writeRegister(Processor.regA1, argv);
	}



	/**
	 * Handle the halt() system call. 
	 */
	private int handleHalt() {

		if (PID != ROOT_PID) {
			Lib.debug(dbgProcess, "Unable to halt.  Only root process may halt operating system");
			return -1;
		}

		Machine.halt();

		Lib.assertNotReached("Machine.halt() did not halt machine!");
		return 0;
	}

	/**
	 * Return first unused file descriptor, or -1 if fileTable full
	 */
	protected int getFileDescriptor() {
		for (int i = 0; i < fileTable.length; i++) {
			if (fileTable[i] == null)
				return i;
		}
		return -1;
	}

	/**
	 * Return whether the given file descriptor is valid
	 */
	private boolean validFileDescriptor(int fileDesc) {

		if (fileDesc < 0 || fileDesc >= fileTable.length)
			return false;

		return fileTable[fileDesc] != null;
	}

	/**
	 * Handle creat(char* filename) system call
	 * @param fileNamePtr
	 * pointer to null terminated file name
	 * @return
	 * file descriptor used to further reference the new file
	 */

	private int handleCreate(int fileNamePtr) {
		return openFile(fileNamePtr, true);
	}

	/**
	 * Handle open(char* filename) system call
	 * @param fileNamePtr
	 * pointer to null terminated file name
	 * @return
	 * file descriptor used to further reference the new file
	 */
	private int handleOpen(int fileNamePtr) {
		return openFile(fileNamePtr, false);
	}

	/**
	 * Open a file and add it to the process file table
	 */
	private int openFile(int fileNamePtr, boolean create) {
		if (!validAddress(fileNamePtr))
			return terminate();

		int fileDesc = getFileDescriptor();
		if (fileDesc == -1) return -1;

		String fileName = readVirtualMemoryString(fileNamePtr, MAXSYSCALLARGLENGTH);

		if (!FileRef.referenceFile(fileName)) return -1;

		OpenFile file = UserKernel.fileSystem.open(fileName, create);

		if (file == null) {
			FileRef.unreferenceFile(fileName);
			return -1;
		}

		fileTable[fileDesc] = file;

		return fileDesc;
	}

	/**
	 * Read data from open file into buffer
	 * @param fileDesc
	 * File descriptor
	 * @param bufferPtr
	 * Pointer to buffer in virtual memory
	 * @param size
	 * How much to read
	 * @return
	 * Number of bytes read, or -1 on error
	 */
	private int handleRead(int fileDesc, int bufferPtr, int size) {
		if (!validAddress(bufferPtr))
			return terminate();
		if (!validFileDescriptor(fileDesc))
			return -1;

		byte buffer[] = new byte[size];
		int bytesRead = fileTable[fileDesc].read(buffer, 0, size);


		if (bytesRead == -1)
			return -1;

		int bytesWritten = writeVirtualMemory(bufferPtr, buffer, 0, bytesRead);

		if (bytesWritten != bytesRead)
			return -1;

		return bytesRead;
	}

	/**
	 * Write data from buffer into an open file
	 * @param fileDesc
	 * File descriptor
	 * @param bufferPtr
	 * Pointer to buffer in virtual memory
	 * @param size
	 * Size of buffer
	 * @return
	 * Number of bytes successfully written, or -1 on error
	 */
	private int handleWrite(int fileDesc, int bufferPtr, int size) {
		if (!validAddress(bufferPtr))
			return terminate();
		if (!validFileDescriptor(fileDesc))
			return -1;

		byte buffer[] = new byte[size];
		int bytesRead = readVirtualMemory(bufferPtr, buffer);
		int bytesWritten = fileTable[fileDesc].write(buffer, 0, bytesRead);

		return bytesWritten;
	}

	/**
	 * Close a file and free its place in the file table
	 * @param fileDesc
	 * Index of file in file table
	 * @return
	 * 0 on success, -1 on error
	 */
	private int handleClose(int fileDesc) {
		if (!validFileDescriptor(fileDesc))
			return -1;

		String fileName = fileTable[fileDesc].getName();

		fileTable[fileDesc].close();
		fileTable[fileDesc] = null;

		return FileRef.unreferenceFile(fileName);
	}

	/**
	 * Mark a file as pending deletion, and remove it if there are no currently active references
	 * If not immediately removed, the file will be removed when all the active references are closed
	 * @param fileNamePtr
	 * Pointer to null terminated string with filename
	 * @return
	 * 0 on success, -1 on error
	 */
	private int handleUnlink(int fileNamePtr) {
		if (!validAddress(fileNamePtr))
			return terminate();

		String fileName = readVirtualMemoryString(fileNamePtr, MAXSYSCALLARGLENGTH);
		return FileRef.deleteFile(fileName);
	}

	/**
	 * Handle spawning a new process
	 * @param fileNamePtr
	 * Pointer to null terminated string containing executable name
	 * @param argc
	 * Number of arguments to pass new process
	 * @param argvPtr
	 * Array of null terminated strings containing arguments
	 * @return
	 * PID of child process, or -1 on failure
	 */
	private int handleExec(int fileNamePtr, int argc, int argvPtr) {
		if (!validAddress(fileNamePtr) || !validAddress(argv)){
			return terminate();
		}

		String fileName = readVirtualMemoryString(fileNamePtr, MAXSYSCALLARGLENGTH);

		if (fileName == null || !fileName.endsWith(".coff")){
			return -1;
		}

		String arguments[] = new String[argc];

		int argvLen = argc * 4;	
		byte argvArray[] = new byte[argvLen];
		if (argvLen != readVirtualMemory(argvPtr, argvArray)) {
			return -1;
		}

		for (int i = 0; i < argc; i++) {
			int pointer = Lib.bytesToInt(argvArray, i*4);

			if (!validAddress(pointer))return -1;

			arguments[i] = readVirtualMemoryString(pointer, MAXSYSCALLARGLENGTH);
		}

		UserProcess newChild = newUserProcess();
		newChild.parent = this;

		children.put(newChild.PID, new ChildProcess(newChild));

		newChild.execute(fileName, arguments);

		return newChild.PID;
	}

	/**
	 * Handle exiting and cleanup of a process
	 * @param status
	 * Integer exit status, or null if exiting due to unhandled exception
	 * @return
	 * Irrelevant - user process never sees this syscall return
	 */
	private int handleExit(Integer status) {
		joinLock.acquire();

		if (parent != null) {
			parent.notifyChildExitStatus(PID, status);
		}

		for (ChildProcess child : children.values()){
			if (child.process != null){
				child.process.disown();
			}
		}
		children = null;

		for (int fileDesc = 0; fileDesc < fileTable.length; fileDesc++){
			if (validFileDescriptor(fileDesc)) {
				handleClose(fileDesc);
			}
		}

		unloadSections();

		exited = true;
		waitingToJoin.wakeAll();
		joinLock.release();

		sharedStateLock.acquire();
		if (--runningProcesses == 0){
			Kernel.kernel.terminate();
		}
		sharedStateLock.release();

		KThread.finish();

		return 0;
	}

	/**
	 * Called on a parent process by an exiting child to inform them that the child has terminated.
	 * @param childPID
	 * @param childStatus
	 * Value of the exit status, or null if exited due to unhandled exception
	 */
	private void notifyChildExitStatus(int childPID, Integer childStatus) {
		ChildProcess child = children.get(childPID);
		if (child == null) return;

		child.process = null;

		child.returnValue = childStatus;
	}

	private void disown() {
		parent = null;

	}

	private int terminate() {
		handleExit(null);
		return -1;
	}

	/**
	 * Wait for child process to exit and transfer exit value
	 * @param pid
	 * Pid of process to join on
	 * @param statusPtr
	 * Pointer to store process exit status
	 * @return
	 * -1 on attempt to join non child process
	 * 1 if child exited due to unhandled exception
	 * 0 if child exited cleanly
	 */
	private int handleJoin(int pid, int statusPtr) {
		if (!validAddress(statusPtr)){
			return terminate();
		}

		ChildProcess child = children.get(pid);

		if (child == null){
			return -1;
		}

		if (child.process != null){
			child.process.joinProcess();
		}
		children.remove(pid);

		
		if (child.returnValue == null){
			return 0;
		}

		writeVirtualMemory(statusPtr, Lib.bytesFromInt(child.returnValue));

		return 1;
	}

	/**
	 * Cause caller to sleep until this process has exited
	 */
	private void joinProcess() {
		joinLock.acquire();
		while (!exited)
			waitingToJoin.sleep();
		joinLock.release();
	}

	private static final int syscallHalt = 0, syscallExit = 1, syscallExec = 2,
			syscallJoin = 3, syscallCreate = 4, syscallOpen = 5, syscallRead = 6,
			syscallWrite = 7, syscallClose = 8, syscallUnlink = 9;

	/**
	 * Handle a syscall exception. Called by <tt>handleException()</tt>. The
	 * <i>syscall</i> argument identifies which syscall the user executed:
	 *
	 * <table>
	 * <tr><td>syscall#</td><td>syscall prototype</td></tr>
	 * <tr><td>0</td><td><tt>void halt();</tt></td></tr>
	 * <tr><td>1</td><td><tt>void exit(int status);</tt></td></tr>
	 * <tr><td>2</td><td><tt>int  exec(char *name, int argc, char **argv);
	 * 								</tt></td></tr>
	 * <tr><td>3</td><td><tt>int  join(int pid, int *status);</tt></td></tr>
	 * <tr><td>4</td><td><tt>int  creat(char *name);</tt></td></tr>
	 * <tr><td>5</td><td><tt>int  open(char *name);</tt></td></tr>
	 * <tr><td>6</td><td><tt>int  read(int fd, char *buffer, int size);
	 *								</tt></td></tr>
	 * <tr><td>7</td><td><tt>int  write(int fd, char *buffer, int size);
	 *								</tt></td></tr>
	 * <tr><td>8</td><td><tt>int  close(int fd);</tt></td></tr>
	 * <tr><td>9</td><td><tt>int  unlink(char *name);</tt></td></tr>
	 * </table>
	 * 
	 * @param	syscall	the syscall number.
	 * @param	a0	the first syscall argument.
	 * @param	a1	the second syscall argument.
	 * @param	a2	the third syscall argument.
	 * @param	a3	the fourth syscall argument.
	 * @return	the value to be returned to the user.
	 */
	public int handleSyscall(int syscall, int a0, int a1, int a2, int a3) {
		switch (syscall) {
		case syscallHalt:
			return handleHalt();
		case syscallExit:
			return handleExit(a0);
		case syscallExec:
			return handleExec(a0, a1, a2);
		case syscallJoin:
			return handleJoin(a0, a1);

		case syscallCreate:
			return handleCreate(a0);
		case syscallOpen:
			return handleOpen(a0);
		case syscallRead:
			return handleRead(a0, a1, a2);
		case syscallWrite:
			return handleWrite(a0, a1, a2);
		case syscallClose:
			return handleClose(a0);
		case syscallUnlink:
			return handleUnlink(a0);




		default:
			Lib.debug(dbgProcess, "Unknown syscall " + syscall);
			Lib.assertNotReached("Unknown system call!");
		}
		return 0;
	}

	/**
	 * Handle a user exception. Called by
	 * <tt>UserKernel.exceptionHandler()</tt>. The
	 * <i>cause</i> argument identifies which exception occurred; see the
	 * <tt>Processor.exceptionZZZ</tt> constants.
	 *
	 * @param	cause	the user exception that occurred.
	 */
	public void handleException(int cause) {
		Processor processor = Machine.processor();

		switch (cause) {
		case Processor.exceptionSyscall:
			int result = handleSyscall(processor.readRegister(Processor.regV0),
					processor.readRegister(Processor.regA0),
					processor.readRegister(Processor.regA1),
					processor.readRegister(Processor.regA2),
					processor.readRegister(Processor.regA3)
					);
			processor.writeRegister(Processor.regV0, result);
			processor.advancePC();
			break;				       

		default:
			Lib.debug(dbgProcess, "Unexpected exception: " +
					Processor.exceptionNames[cause]);
			terminate();
			Lib.assertNotReached("Unexpected exception");
		}
	}

	private static class ChildProcess {
		public Integer returnValue;
		public UserProcess process;

		public ChildProcess(UserProcess child) {
			process = child;
			returnValue = null;
		}
	}

	protected static class FileRef {
		int references;
		boolean delete;
		
		public static boolean referenceFile(String fileName) {
			FileRef ref = updateFileReference(fileName);
			boolean canReference = !ref.delete;

			if (canReference) ref.references++;
			finishUpdateFileReference();

			return canReference;
		}



		public static int deleteFile(String fileName) {
			FileRef ref = updateFileReference(fileName);
			ref.delete = true;
			int ret = removeIfNecessary(fileName, ref);
			finishUpdateFileReference();
			return ret;
		}

		public static int unreferenceFile(String fileName) {
			FileRef ref = updateFileReference(fileName);
			ref.references--;
			Lib.assertTrue(ref.references >= 0);
			int ret = removeIfNecessary(fileName, ref);
			finishUpdateFileReference();
			return ret;
		}



		private static int removeIfNecessary(String fileName, FileRef ref) {
			if (ref.references <= 0) {
				globalFileReferences.remove(fileName);
				if (ref.delete == true) {
					if (!UserKernel.fileSystem.remove(fileName))
						return -1;
				}
			}

			return 0;
		}

		private static FileRef updateFileReference(String fileName) {
			globalFileReferencesLock.acquire();
			FileRef ref = globalFileReferences.get(fileName);

			if (ref == null) {
				ref = new FileRef();
				globalFileReferences.put(fileName, ref);
			}
			return ref;
		}

		private static void finishUpdateFileReference() {
			globalFileReferencesLock.release();
		}
		
		/** Global file reference tracker & lock */
		private static HashMap<String, FileRef> globalFileReferences = new HashMap<String, FileRef> ();
		private static Lock globalFileReferencesLock = new Lock();

	}

	/** The program being run by this process. */
	protected Coff coff;

	/** This process's page table. */
	protected TranslationEntry[] pageTable;
	/** The number of contiguous pages occupied by the program. */
	protected int numPages;

	/** The number of pages in the program's stack. */
	protected final int stackPages = 8;

	private int initialPC, initialSP;
	private int argc, argv;

	private static final int pageSize = Processor.pageSize;
	private static final char dbgProcess = 'a';

	public static final int ROOT_PID = 0;

	protected OpenFile[] fileTable = new OpenFile[16];
	private static final int MAXSYSCALLARGLENGTH = 256;

	/** Lock to protect static variables */
	private static Lock sharedStateLock = new Lock();

	/** Process ID */
	private static int nextPID = 0;
	protected int PID;

	/** Parent/Child process tree */
	protected UserProcess parent;
	private HashMap<Integer, ChildProcess> children = new HashMap<Integer, ChildProcess> ();

	/**
	 * A lock to protect memory accesses.
	 */
	private Lock memoryAccessLock = new Lock();

	/** Join condition */
	private boolean exited = false;
	private Lock joinLock = new Lock();
	private Condition waitingToJoin;

	/** Number of processes */
	private static int runningProcesses = 0;

}
