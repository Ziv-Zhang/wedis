package com.wedis.redis.io;

import java.io.DataInput;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InvalidClassException;
import java.io.InvalidObjectException;
import java.io.NotActiveException;
import java.io.ObjectInputStream;
import java.io.ObjectInputValidation;
import java.io.SerializablePermission;
import java.io.StreamCorruptedException;
import java.io.UTFDataFormatException;
import java.io.WriteAbortedException;
import java.security.AccessControlContext;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.Arrays;
import java.util.HashMap;

import com.wedis.base.enums.ResponseCode;
import com.wedis.base.exception.CoreException;

import sun.misc.ObjectInputFilter;
import sun.misc.ObjectStreamClassValidator;

public class JdkInputStream extends ObjectInputStream {
	/** handle value representing null */
	private static final int NULL_HANDLE = -1;

	/** marker for unshared objects in internal handle table */
	private static final Object unsharedMarker = new Object();

	/** table mapping primitive type names to corresponding class objects */
	private static final HashMap<String, Class<?>> primClasses = new HashMap<>(8, 1.0F);
	static{
		primClasses.put("boolean", boolean.class);
		primClasses.put("byte", byte.class);
		primClasses.put("char", char.class);
		primClasses.put("short", short.class);
		primClasses.put("int", int.class);
		primClasses.put("long", long.class);
		primClasses.put("float", float.class);
		primClasses.put("double", double.class);
		primClasses.put("void", void.class);
	}

	/** filter stream for handling block data conversion */
	private final BlockDataInputStream bin;
	/** validation callback list */
	private final ValidationList vlist;
	/** recursion depth */
	private long depth;
	/**
	 * Total number of references to any type of object, class, enum, proxy,
	 * etc.
	 */
	private long totalObjectRefs;
	/** whether stream is closed */
	private boolean closed;

	/** wire handle -> obj/exception map */
	private final HandleTable handles;
	/** scratch field for passing handle values up/down call stack */
	private int passHandle = NULL_HANDLE;
	/** flag set when at end of field value block with no TC_ENDBLOCKDATA */
	private boolean defaultDataEnd = false;

	/** buffer for reading primitive field values */
	private byte[] primVals;

	/** if true, invoke resolveObject() */
	private boolean enableResolve;

	/**
	 * Context during upcalls to class-defined readObject methods; holds object
	 * currently being deserialized and descriptor for current class. Null when
	 * not during readObject upcall.
	 */
	private SerialCallbackContext curContext;

	/**
	 * Filter of class descriptors and classes read from the stream; may be
	 * null.
	 */
	private ObjectInputFilter serialFilter;

	public JdkInputStream(InputStream in) throws IOException {
		bin = new BlockDataInputStream(in);
		handles = new HandleTable(10);
		vlist = new ValidationList();
		serialFilter = ObjectInputFilter.Config.getSerialFilter();
		readStreamHeader();
		bin.setBlockDataMode(true);
	}

	@Override
	protected Object readObjectOverride() throws IOException, ClassNotFoundException {
		// if nested read, passHandle contains handle of enclosing object
		int outerHandle = passHandle;
		try{
			Object obj = readObject0(false);
			handles.markDependency(outerHandle, passHandle);
			ClassNotFoundException ex = handles.lookupException(passHandle);
			if(ex != null){
				throw ex;
			}
			if(depth == 0){
				vlist.doCallbacks();
			}
			return obj;
		}finally{
			passHandle = outerHandle;
			if(closed && depth == 0){
				clear();
			}
		}
	}

	protected void readStreamHeader() throws IOException, StreamCorruptedException {
		short s0 = bin.readShort();
		short s1 = bin.readShort();
		if(s0 != STREAM_MAGIC || s1 != STREAM_VERSION){
			throw new StreamCorruptedException(
				String.format("invalid stream header: %04X%04X", s0, s1));
		}
	}

	private ObjectStreamDesc readClassDesc() throws IOException {
		ObjectStreamDesc desc = new ObjectStreamDesc();
		desc.readNonProxy(this);
		return desc;
	}

	public int read() throws IOException {
		return bin.read();
	}

	public int read(byte[] buf, int off, int len) throws IOException {
		if(buf == null){
			throw new NullPointerException();
		}
		int endoff = off + len;
		if(off < 0 || len < 0 || endoff > buf.length || endoff < 0){
			throw new IndexOutOfBoundsException();
		}
		return bin.read(buf, off, len, false);
	}

	public int available() throws IOException {
		return bin.available();
	}

	public void close() throws IOException {
		closed = true;
		if(depth == 0){
			clear();
		}
		bin.close();
	}

	public boolean readBoolean() throws IOException {
		return bin.readBoolean();
	}

	public byte readByte() throws IOException {
		return bin.readByte();
	}

	public int readUnsignedByte() throws IOException {
		return bin.readUnsignedByte();
	}

	public char readChar() throws IOException {
		return bin.readChar();
	}

	public short readShort() throws IOException {
		return bin.readShort();
	}

	public int readUnsignedShort() throws IOException {
		return bin.readUnsignedShort();
	}

	public int readInt() throws IOException {
		return bin.readInt();
	}

	public long readLong() throws IOException {
		return bin.readLong();
	}

	public float readFloat() throws IOException {
		return bin.readFloat();
	}

	public double readDouble() throws IOException {
		return bin.readDouble();
	}

	public void readFully(byte[] buf) throws IOException {
		bin.readFully(buf, 0, buf.length, false);
	}

	public void readFully(byte[] buf, int off, int len) throws IOException {
		int endoff = off + len;
		if(off < 0 || len < 0 || endoff > buf.length || endoff < 0){
			throw new IndexOutOfBoundsException();
		}
		bin.readFully(buf, off, len, false);
	}

	public int skipBytes(int len) throws IOException {
		return bin.skipBytes(len);
	}

	@Deprecated
	public String readLine() throws IOException {
		return bin.readLine();
	}

	public String readUTF() throws IOException {
		return bin.readUTF();
	}

	private final ObjectInputFilter getInternalObjectInputFilter() {
		return serialFilter;
	}

	private final void setInternalObjectInputFilter(ObjectInputFilter filter) {
		SecurityManager sm = System.getSecurityManager();
		if(sm != null){
			sm.checkPermission(new SerializablePermission("serialFilter"));
		}
		// Allow replacement of the process-wide filter if not already set
		if(serialFilter != null && serialFilter != ObjectInputFilter.Config.getSerialFilter()){
			throw new IllegalStateException("filter can not be set more than once");
		}
		this.serialFilter = filter;
	}

	private static boolean auditSubclass(final Class<?> subcl) {
		Boolean result = AccessController.doPrivileged(new PrivilegedAction<Boolean>() {
			public Boolean run() {
				for(Class<?> cl = subcl; cl != JdkInputStream.class; cl = cl.getSuperclass()){
					try{
						cl.getDeclaredMethod("readUnshared", (Class[])null);
						return Boolean.FALSE;
					}catch(NoSuchMethodException ex){
					}
					try{
						cl.getDeclaredMethod("readFields", (Class[])null);
						return Boolean.FALSE;
					}catch(NoSuchMethodException ex){
					}
				}
				return Boolean.TRUE;
			}
		});
		return result.booleanValue();
	}

	private void clear() {
		handles.clear();
		vlist.clear();
	}

	private Object readObject0(boolean unshared) throws IOException {
		boolean oldMode = bin.getBlockDataMode();
		if(oldMode){
			int remain = bin.currentBlockRemaining();
			if(remain > 0){
				throw new CoreException(ResponseCode.JDK_DESERIALIZE_FAILED,
					"remaining data:" + remain);
			}else if(defaultDataEnd){
				throw new CoreException(ResponseCode.JDK_DESERIALIZE_FAILED,
					"missing TC_ENDBLOCKDATA tag");
			}
			bin.setBlockDataMode(false);
		}

		byte tc;
		while((tc = bin.peekByte()) == TC_RESET){
			bin.readByte();
			handleReset();
		}

		depth++;
		totalObjectRefs++;
		try{
			switch(tc){
			case TC_NULL:
				return readNull();

			case TC_REFERENCE:
				return readHandle(unshared);

			case TC_CLASS:
				return readClass(unshared);

			case TC_CLASSDESC:
			case TC_PROXYCLASSDESC:
				return readClassDesc(unshared);

			case TC_STRING:
			case TC_LONGSTRING:
				return checkResolve(readString(unshared));

			case TC_ARRAY:
				return checkResolve(readArray(unshared));

			case TC_ENUM:
				return checkResolve(readEnum(unshared));

			case TC_OBJECT:
				return checkResolve(readOrdinaryObject(unshared));

			case TC_EXCEPTION:
				IOException ex = readFatalException();
				throw new WriteAbortedException("writing aborted", ex);

			case TC_BLOCKDATA:
			case TC_BLOCKDATALONG:
				if(oldMode){
					bin.setBlockDataMode(true);
					bin.peek(); // force header read
					throw new CoreException(ResponseCode.JDK_DESERIALIZE_FAILED,
						"remaining data:" + bin.currentBlockRemaining());
				}else{
					throw new CoreException(ResponseCode.JDK_DESERIALIZE_FAILED,
						"unexpected block data");
				}

			case TC_ENDBLOCKDATA:
				if(oldMode){
					throw new CoreException(ResponseCode.JDK_DESERIALIZE_FAILED,
						"missing TC_ENDBLOCKDATA tag");
				}else{
					throw new CoreException(ResponseCode.JDK_DESERIALIZE_FAILED,
						"unexpected end of block data");
				}

			default:
				throw new CoreException(ResponseCode.JDK_DESERIALIZE_FAILED,
					String.format("invalid type code: %02X", tc));
			}
		}finally{
			depth--;
			bin.setBlockDataMode(oldMode);
		}
	}

	private Object checkResolve(Object obj) throws IOException {
		if(!enableResolve || handles.lookupException(passHandle) != null){
			return obj;
		}
		return obj;
	}

	String readTypeString() throws IOException {
		int oldHandle = passHandle;
		try{
			byte tc = bin.peekByte();
			switch(tc){
			case TC_NULL:
				return (String)readNull();

			case TC_REFERENCE:
				return (String)readHandle(false);

			case TC_STRING:
			case TC_LONGSTRING:
				return readString(false);

			default:
				throw new StreamCorruptedException(String.format("invalid type code: %02X", tc));
			}
		}finally{
			passHandle = oldHandle;
		}
	}

	/**
	 * Reads in null code, sets passHandle to NULL_HANDLE and returns null.
	 */
	private Object readNull() throws IOException {
		if(bin.readByte() != TC_NULL){
			throw new InternalError();
		}
		passHandle = NULL_HANDLE;
		return null;
	}

	/**
	 * Reads in object handle, sets passHandle to the read handle, and returns
	 * object associated with the handle.
	 */
	private Object readHandle(boolean unshared) throws IOException {
		if(bin.readByte() != TC_REFERENCE){
			throw new CoreException(ResponseCode.JDK_DESERIALIZE_FAILED);
		}
		passHandle = bin.readInt() - baseWireHandle;
		if(passHandle < 0 || passHandle >= handles.size()){
			throw new CoreException(ResponseCode.JDK_DESERIALIZE_FAILED,
				String.format("invalid handle value: %08X", passHandle + baseWireHandle));
		}
		if(unshared){
			// REMIND: what type of exception to throw here?
			throw new CoreException(ResponseCode.JDK_DESERIALIZE_FAILED,
				"cannot read back reference as unshared");
		}

		Object obj = handles.lookupObject(passHandle);
		if(obj == unsharedMarker){
			// REMIND: what type of exception to throw here?
			throw new CoreException(ResponseCode.JDK_DESERIALIZE_FAILED,
				"cannot read back reference to unshared object");
		}
		return obj;
	}

	private String readClass(boolean unshared) throws IOException {
		if(bin.readByte() != TC_CLASS){
			throw new InternalError();
		}
		ObjectStreamDesc desc = readClassDesc(false);
		String cl = desc.getName();
		passHandle = handles.assign(unshared ? unsharedMarker : cl);

		handles.finish(passHandle);
		return cl;
	}

	/**
	 * Reads in and returns (possibly null) class descriptor. Sets passHandle to
	 * class descriptor's assigned handle. If class descriptor cannot be
	 * resolved to a class in the local VM, a ClassNotFoundException is
	 * associated with the class descriptor's handle.
	 */
	private ObjectStreamDesc readClassDesc(boolean unshared) throws IOException {
		byte tc = bin.peekByte();
		ObjectStreamDesc descriptor;
		switch(tc){
		case TC_NULL:
			descriptor = (ObjectStreamDesc)readNull();
			break;
		case TC_REFERENCE:
			descriptor = (ObjectStreamDesc)readHandle(unshared);
			break;
		case TC_PROXYCLASSDESC:
			descriptor = readProxyDesc(unshared);
			break;
		case TC_CLASSDESC:
			descriptor = readNonProxyDesc(unshared);
			break;
		default:
			throw new CoreException(ResponseCode.JDK_DESERIALIZE_FAILED,
				String.format("invalid type code: %02X", tc));
		}
		return descriptor;
	}

	private boolean isCustomSubclass() {
		// Return true if this class is a custom subclass of ObjectInputStream
		return getClass().getClassLoader() != JdkInputStream.class.getClassLoader();
	}

	private ObjectStreamDesc readProxyDesc(boolean unshared) throws IOException {
		if(bin.readByte() != TC_PROXYCLASSDESC){
			throw new InternalError();
		}

		ObjectStreamDesc desc = new ObjectStreamDesc();
		int descHandle = handles.assign(unshared ? unsharedMarker : desc);
		passHandle = NULL_HANDLE;

		int numIfaces = bin.readInt();
		if(numIfaces > 65535){
			throw new InvalidObjectException("interface limit exceeded: " + numIfaces);
		}
		String[] ifaces = new String[numIfaces];
		for(int i = 0; i < numIfaces; i++){
			ifaces[i] = bin.readUTF();
		}

		bin.setBlockDataMode(true);
		skipCustomData();

		try{
			totalObjectRefs++;
			depth++;
			desc.initProxy(readClassDesc(false));
		}finally{
			depth--;
		}

		handles.finish(descHandle);
		passHandle = descHandle;
		return desc;
	}

	private ObjectStreamDesc readNonProxyDesc(boolean unshared) throws IOException {
		if(bin.readByte() != TC_CLASSDESC){
			throw new InternalError();
		}

		ObjectStreamDesc desc = new ObjectStreamDesc();
		int descHandle = handles.assign(unshared ? unsharedMarker : desc);
		passHandle = NULL_HANDLE;

		ObjectStreamDesc readDesc = readClassDesc();

		bin.setBlockDataMode(true);

		skipCustomData();

		try{
			totalObjectRefs++;
			depth++;
			desc.initNonProxy(readDesc, resolveClass(readDesc), readClassDesc(false));
		}finally{
			depth--;
		}

		handles.finish(descHandle);
		passHandle = descHandle;

		return desc;
	}

	protected Class<?> resolveClass(ObjectStreamDesc desc) throws IOException {
		String name = desc.getName();
		try{
			return Class.forName(name, false, latestUserDefinedLoader());
		}catch(ClassNotFoundException ex){
			Class<?> cl = primClasses.get(name);
			if(cl != null){
				return cl;
			}else{
				desc.hasClass(false);
				return HashMap.class;
			}
		}
	}

	private String readString(boolean unshared) throws IOException {
		String str;
		byte tc = bin.readByte();
		switch(tc){
		case TC_STRING:
			str = bin.readUTF();
			break;

		case TC_LONGSTRING:
			str = bin.readLongUTF();
			break;

		default:
			throw new StreamCorruptedException(String.format("invalid type code: %02X", tc));
		}
		passHandle = handles.assign(unshared ? unsharedMarker : str);
		handles.finish(passHandle);
		return str;
	}

	private Object readArray(boolean unshared) throws IOException {
		if(bin.readByte() != TC_ARRAY){
			throw new InternalError();
		}

		ObjectStreamDesc desc = readClassDesc(false);
		int len = bin.readInt();

		Object array = new Object[len];

		String name = null;
		if(desc == null || (name = desc.getName()) == null || name.length() < 2
			|| name.charAt(0) != '['){
			Object[] oa = (Object[])array;
			for(int i = 0; i < len; i++){
				oa[i] = readObject0(false);
			}
			return array;
		}

		int arrayHandle = handles.assign(unshared ? unsharedMarker : array);

		switch(name.charAt(1)){
		case 'Z':
			// boolean
			array = new boolean[len];
			bin.readBooleans((boolean[])array, 0, len);
			break;
		case 'B':
			// byte
			array = new byte[len];
			bin.readFully((byte[])array, 0, len, true);
			break;
		case 'C':
			// char
			array = new char[len];
			bin.readChars((char[])array, 0, len);
			break;
		case 'S':
			// short
			array = new short[len];
			bin.readShorts((short[])array, 0, len);
			break;
		case 'I':
			// int
			array = new int[len];
			bin.readInts((int[])array, 0, len);
			break;
		case 'F':
			// float
			array = new float[len];
			bin.readFloats((float[])array, 0, len);
			break;
		case 'J':
			// long
			array = new long[len];
			bin.readLongs((long[])array, 0, len);
			break;
		case 'D':
			// double
			array = new double[len];
			bin.readDoubles((double[])array, 0, len);
			break;
		default:
			// object
			Object[] oa = (Object[])array;
			for(int i = 0; i < len; i++){
				oa[i] = readObject0(false);
				handles.markDependency(arrayHandle, passHandle);
			}
			break;
		}

		handles.finish(arrayHandle);
		passHandle = arrayHandle;
		return array;
	}

	private String readEnum(boolean unshared) throws IOException {
		if(bin.readByte() != TC_ENUM){
			throw new InternalError();
		}

		ObjectStreamDesc desc = readClassDesc(false);
		if(!desc.isEnum()){
			throw new InvalidClassException("non-enum class: " + desc);
		}

		int enumHandle = handles.assign(unshared ? unsharedMarker : null);

		String name = readString(false);
		if(!unshared){
			handles.setObject(enumHandle, name);
		}

		handles.finish(enumHandle);
		passHandle = enumHandle;
		return name;
	}

	private Object readOrdinaryObject(boolean unshared) throws IOException {
		if(bin.readByte() != TC_OBJECT){
			throw new InternalError();
		}

		ObjectStreamDesc desc = readClassDesc(false);

		Object obj = desc.newInstance();
		passHandle = handles.assign(unshared ? unsharedMarker : obj);
		if(desc.isExternalizable()){
			throw new CoreException(ResponseCode.JDK_DESERIALIZE_FAILED, "暂不支持Externalizable");
		}else{
			readSerialData(obj, desc);
		}

		handles.finish(passHandle);

		return obj;
	}

	private void readSerialData(Object obj, ObjectStreamDesc desc) throws IOException {
		ObjectStreamDesc[] slots = desc.getClassDataLayout();
		for(int i = 0; i < slots.length; i++){
			ObjectStreamDesc slotDesc = slots[i];
			if(obj == null || handles.lookupException(passHandle) != null){
				defaultReadFields(null, slotDesc); // skip field values
			}else if(slotDesc.hasReadObjectMethod()){
				ThreadDeath t = null;
				boolean reset = false;
				SerialCallbackContext oldContext = curContext;
				if(oldContext != null)
					oldContext.check();
				try{
					curContext = new SerialCallbackContext(obj, slotDesc);

					bin.setBlockDataMode(true);
					slotDesc.invokeReadObject(obj, this);
				}catch(ClassNotFoundException ex){
					handles.markException(passHandle, ex);
				}finally{
					do{
						try{
							curContext.setUsed();
							if(oldContext != null)
								oldContext.check();
							curContext = oldContext;
							reset = true;
						}catch(ThreadDeath x){
							t = x; // defer until reset is true
						}
					}while(!reset);
					if(t != null)
						throw t;
				}

				defaultDataEnd = false;
			}else{
				defaultReadFields(obj, slotDesc);
			}

			if(slotDesc.hasWriteObjectData()){
				skipCustomData();
			}else{
				bin.setBlockDataMode(false);
			}
		}
	}

	private void defaultReadFields(Object obj, ObjectStreamDesc desc) throws IOException {
		int primDataSize = desc.getPrimDataSize();
		if(primVals == null || primVals.length < primDataSize){
			primVals = new byte[primDataSize];
		}
		bin.readFully(primVals, 0, primDataSize, false);
		if(obj != null){
			desc.setPrimFieldValues(obj, primVals);
		}

		int objHandle = passHandle;
		ObjectStreamField[] fields = desc.getFields(false);
		Object[] objVals = new Object[desc.getNumObjFields()];
		int numPrimFields = fields.length - objVals.length;
		for(int i = 0; i < objVals.length; i++){
			ObjectStreamField f = fields[numPrimFields + i];
			objVals[i] = readObject0(f.isUnshared());
			if(f.getField() != null){
				handles.markDependency(objHandle, passHandle);
			}
		}
		if(obj != null){
			desc.setObjFieldValues(obj, objVals);
		}
		passHandle = objHandle;
	}

	private void skipCustomData() throws IOException {
		int oldHandle = passHandle;
		for(;;){
			if(bin.getBlockDataMode()){
				bin.skipBlockData();
				bin.setBlockDataMode(false);
			}
			switch(bin.peekByte()){
			case TC_BLOCKDATA:
			case TC_BLOCKDATALONG:
				bin.setBlockDataMode(true);
				break;

			case TC_ENDBLOCKDATA:
				bin.readByte();
				passHandle = oldHandle;
				return;

			default:
				readObject0(false);
				break;
			}
		}
	}

	private IOException readFatalException() throws IOException {
		if(bin.readByte() != TC_EXCEPTION){
			throw new InternalError();
		}
		clear();
		return (IOException)readObject0(false);
	}

	private void handleReset() throws StreamCorruptedException {
		if(depth > 0){
			throw new StreamCorruptedException("unexpected reset; recursion depth: " + depth);
		}
		clear();
	}

	private static native void bytesToFloats(byte[] src, int srcpos, float[] dst, int dstpos,
		int nfloats);

	private static native void bytesToDoubles(byte[] src, int srcpos, double[] dst, int dstpos,
		int ndoubles);

	private static ClassLoader latestUserDefinedLoader() {
		return sun.misc.VM.latestUserDefinedLoader();
	}

	private static class ValidationList {

		private static class Callback {
			final ObjectInputValidation obj;
			final int priority;
			Callback next;
			final AccessControlContext acc;

			Callback(ObjectInputValidation obj, int priority, Callback next,
				AccessControlContext acc) {
				this.obj = obj;
				this.priority = priority;
				this.next = next;
				this.acc = acc;
			}
		}

		/** linked list of callbacks */
		private Callback list;

		/**
		 * Creates new (empty) ValidationList.
		 */
		ValidationList() {
		}

		/**
		 * Registers callback. Throws InvalidObjectException if callback object
		 * is null.
		 */
		void register(ObjectInputValidation obj, int priority) throws InvalidObjectException {
			if(obj == null){
				throw new InvalidObjectException("null callback");
			}

			Callback prev = null, cur = list;
			while(cur != null && priority < cur.priority){
				prev = cur;
				cur = cur.next;
			}
			AccessControlContext acc = AccessController.getContext();
			if(prev != null){
				prev.next = new Callback(obj, priority, cur, acc);
			}else{
				list = new Callback(obj, priority, list, acc);
			}
		}

		/**
		 * Invokes all registered callbacks and clears the callback list.
		 * Callbacks with higher priorities are called first; those with equal
		 * priorities may be called in any order. If any of the callbacks throws
		 * an InvalidObjectException, the callback process is terminated and the
		 * exception propagated upwards.
		 */
		void doCallbacks() throws InvalidObjectException {
			try{
				while(list != null){
					AccessController.doPrivileged(new PrivilegedExceptionAction<Void>() {
						public Void run() throws InvalidObjectException {
							list.obj.validateObject();
							return null;
						}
					}, list.acc);
					list = list.next;
				}
			}catch(PrivilegedActionException ex){
				list = null;
				throw (InvalidObjectException)ex.getException();
			}
		}

		/**
		 * Resets the callback list to its initial (empty) state.
		 */
		public void clear() {
			list = null;
		}
	}

	/**
	 * Hold a snapshot of values to be passed to an ObjectInputFilter.
	 */
	static class FilterValues implements ObjectInputFilter.FilterInfo {
		final Class<?> clazz;
		final long arrayLength;
		final long totalObjectRefs;
		final long depth;
		final long streamBytes;

		public FilterValues(Class<?> clazz, long arrayLength, long totalObjectRefs, long depth,
			long streamBytes) {
			this.clazz = clazz;
			this.arrayLength = arrayLength;
			this.totalObjectRefs = totalObjectRefs;
			this.depth = depth;
			this.streamBytes = streamBytes;
		}

		@Override
		public Class<?> serialClass() {
			return clazz;
		}

		@Override
		public long arrayLength() {
			return arrayLength;
		}

		@Override
		public long references() {
			return totalObjectRefs;
		}

		@Override
		public long depth() {
			return depth;
		}

		@Override
		public long streamBytes() {
			return streamBytes;
		}
	}

	/**
	 * Input stream supporting single-byte peek operations.
	 */
	private static class PeekInputStream extends InputStream {

		/** underlying stream */
		private final InputStream in;
		/** peeked byte */
		private int peekb = -1;
		/** total bytes read from the stream */
		private long totalBytesRead = 0;

		/**
		 * Creates new PeekInputStream on top of given underlying stream.
		 */
		PeekInputStream(InputStream in) {
			this.in = in;
		}

		/**
		 * Peeks at next byte value in stream. Similar to read(), except that it
		 * does not consume the read value.
		 */
		int peek() throws IOException {
			if(peekb >= 0){
				return peekb;
			}
			peekb = in.read();
			totalBytesRead += peekb >= 0 ? 1 : 0;
			return peekb;
		}

		public int read() throws IOException {
			if(peekb >= 0){
				int v = peekb;
				peekb = -1;
				return v;
			}else{
				int nbytes = in.read();
				totalBytesRead += nbytes >= 0 ? 1 : 0;
				return nbytes;
			}
		}

		public int read(byte[] b, int off, int len) throws IOException {
			int nbytes;
			if(len == 0){
				return 0;
			}else if(peekb < 0){
				nbytes = in.read(b, off, len);
				totalBytesRead += nbytes >= 0 ? nbytes : 0;
				return nbytes;
			}else{
				b[off++] = (byte)peekb;
				len--;
				peekb = -1;
				nbytes = in.read(b, off, len);
				totalBytesRead += nbytes >= 0 ? nbytes : 0;
				return (nbytes >= 0) ? (nbytes + 1) : 1;
			}
		}

		void readFully(byte[] b, int off, int len) throws IOException {
			int n = 0;
			while(n < len){
				int count = read(b, off + n, len - n);
				if(count < 0){
					throw new EOFException();
				}
				n += count;
			}
		}

		public long skip(long n) throws IOException {
			if(n <= 0){
				return 0;
			}
			int skipped = 0;
			if(peekb >= 0){
				peekb = -1;
				skipped++;
				n--;
			}
			n = skipped + in.skip(n);
			totalBytesRead += n;
			return n;
		}

		public int available() throws IOException {
			return in.available() + ((peekb >= 0) ? 1 : 0);
		}

		public void close() throws IOException {
			in.close();
		}

		public long getBytesRead() {
			return totalBytesRead;
		}
	}

	/**
	 * Input stream with two modes: in default mode, inputs data written in the
	 * same format as DataOutputStream; in "block data" mode, inputs data
	 * bracketed by block data markers (see object serialization specification
	 * for details). Buffering depends on block data mode: when in default mode,
	 * no data is buffered in advance; when in block data mode, all data for the
	 * current data block is read in at once (and buffered).
	 */
	private class BlockDataInputStream extends InputStream implements DataInput {
		/** maximum data block length */
		private static final int MAX_BLOCK_SIZE = 1024;
		/** maximum data block header length */
		private static final int MAX_HEADER_SIZE = 5;
		/** (tunable) length of char buffer (for reading strings) */
		private static final int CHAR_BUF_SIZE = 256;
		/** readBlockHeader() return value indicating header read may block */
		private static final int HEADER_BLOCKED = -2;

		/** buffer for reading general/block data */
		private final byte[] buf = new byte[MAX_BLOCK_SIZE];
		/** buffer for reading block data headers */
		private final byte[] hbuf = new byte[MAX_HEADER_SIZE];
		/** char buffer for fast string reads */
		private final char[] cbuf = new char[CHAR_BUF_SIZE];

		/** block data mode */
		private boolean blkmode = false;

		// block data state fields; values meaningful only when blkmode true
		/** current offset into buf */
		private int pos = 0;
		/** end offset of valid data in buf, or -1 if no more block data */
		private int end = -1;
		/** number of bytes in current block yet to be read from stream */
		private int unread = 0;

		/** underlying stream (wrapped in peekable filter stream) */
		private final PeekInputStream in;
		/** loopback stream (for data reads that span data blocks) */
		private final DataInputStream din;

		/**
		 * Creates new BlockDataInputStream on top of given underlying stream.
		 * Block data mode is turned off by default.
		 */
		BlockDataInputStream(InputStream in) {
			this.in = new PeekInputStream(in);
			din = new DataInputStream(this);
		}

		/**
		 * Sets block data mode to the given mode (true == on, false == off) and
		 * returns the previous mode value. If the new mode is the same as the
		 * old mode, no action is taken. Throws IllegalStateException if block
		 * data mode is being switched from on to off while unconsumed block
		 * data is still present in the stream.
		 */
		boolean setBlockDataMode(boolean newmode) throws IOException {
			if(blkmode == newmode){
				return blkmode;
			}
			if(newmode){
				pos = 0;
				end = 0;
				unread = 0;
			}else if(pos < end){
				throw new IllegalStateException("unread block data");
			}
			blkmode = newmode;
			return !blkmode;
		}

		/**
		 * Returns true if the stream is currently in block data mode, false
		 * otherwise.
		 */
		boolean getBlockDataMode() {
			return blkmode;
		}

		/**
		 * If in block data mode, skips to the end of the current group of data
		 * blocks (but does not unset block data mode). If not in block data
		 * mode, throws an IllegalStateException.
		 */
		void skipBlockData() throws IOException {
			if(!blkmode){
				throw new IllegalStateException("not in block data mode");
			}
			while(end >= 0){
				refill();
			}
		}

		/**
		 * Attempts to read in the next block data header (if any). If canBlock
		 * is false and a full header cannot be read without possibly blocking,
		 * returns HEADER_BLOCKED, else if the next element in the stream is a
		 * block data header, returns the block data length specified by the
		 * header, else returns -1.
		 */
		private int readBlockHeader(boolean canBlock) throws IOException {
			if(defaultDataEnd){
				/*
				 * Fix for 4360508: stream is currently at the end of a field
				 * value block written via default serialization; since there is
				 * no terminating TC_ENDBLOCKDATA tag, simulate
				 * end-of-custom-data behavior explicitly.
				 */
				return -1;
			}
			try{
				for(;;){
					int avail = canBlock ? Integer.MAX_VALUE : in.available();
					if(avail == 0){
						return HEADER_BLOCKED;
					}

					int tc = in.peek();
					switch(tc){
					case TC_BLOCKDATA:
						if(avail < 2){
							return HEADER_BLOCKED;
						}
						in.readFully(hbuf, 0, 2);
						return hbuf[1] & 0xFF;

					case TC_BLOCKDATALONG:
						if(avail < 5){
							return HEADER_BLOCKED;
						}
						in.readFully(hbuf, 0, 5);
						int len = Bits.getInt(hbuf, 1);
						if(len < 0){
							throw new StreamCorruptedException(
								"illegal block data header length: " + len);
						}
						return len;

					/*
					 * TC_RESETs may occur in between data blocks.
					 * Unfortunately, this case must be parsed at a lower level
					 * than other typecodes, since primitive data reads may span
					 * data blocks separated by a TC_RESET.
					 */
					case TC_RESET:
						in.read();
						handleReset();
						break;

					default:
						if(tc >= 0 && (tc < TC_BASE || tc > TC_MAX)){
							throw new StreamCorruptedException(
								String.format("invalid type code: %02X", tc));
						}
						return -1;
					}
				}
			}catch(EOFException ex){
				throw new StreamCorruptedException(
					"unexpected EOF while reading block data header");
			}
		}

		/**
		 * Refills internal buffer buf with block data. Any data in buf at the
		 * time of the call is considered consumed. Sets the pos, end, and
		 * unread fields to reflect the new amount of available block data; if
		 * the next element in the stream is not a data block, sets pos and
		 * unread to 0 and end to -1.
		 */
		private void refill() throws IOException {
			try{
				do{
					pos = 0;
					if(unread > 0){
						int n = in.read(buf, 0, Math.min(unread, MAX_BLOCK_SIZE));
						if(n >= 0){
							end = n;
							unread -= n;
						}else{
							throw new StreamCorruptedException(
								"unexpected EOF in middle of data block");
						}
					}else{
						int n = readBlockHeader(true);
						if(n >= 0){
							end = 0;
							unread = n;
						}else{
							end = -1;
							unread = 0;
						}
					}
				}while(pos == end);
			}catch(IOException ex){
				pos = 0;
				end = -1;
				unread = 0;
				throw ex;
			}
		}

		/**
		 * If in block data mode, returns the number of unconsumed bytes
		 * remaining in the current data block. If not in block data mode,
		 * throws an IllegalStateException.
		 */
		int currentBlockRemaining() {
			if(blkmode){
				return (end >= 0) ? (end - pos) + unread : 0;
			}else{
				throw new IllegalStateException();
			}
		}

		/**
		 * Peeks at (but does not consume) and returns the next byte value in
		 * the stream, or -1 if the end of the stream/block data (if in block
		 * data mode) has been reached.
		 */
		int peek() throws IOException {
			if(blkmode){
				if(pos == end){
					refill();
				}
				return (end >= 0) ? (buf[pos] & 0xFF) : -1;
			}else{
				return in.peek();
			}
		}

		/**
		 * Peeks at (but does not consume) and returns the next byte value in
		 * the stream, or throws EOFException if end of stream/block data has
		 * been reached.
		 */
		byte peekByte() throws IOException {
			int val = peek();
			if(val < 0){
				throw new EOFException();
			}
			return (byte)val;
		}


		/* ----------------- generic input stream methods ------------------ */
		/*
		 * The following methods are equivalent to their counterparts in
		 * InputStream, except that they interpret data block boundaries and
		 * read the requested data from within data blocks when in block data
		 * mode.
		 */

		public int read() throws IOException {
			if(blkmode){
				if(pos == end){
					refill();
				}
				return (end >= 0) ? (buf[pos++] & 0xFF) : -1;
			}else{
				return in.read();
			}
		}

		public int read(byte[] b, int off, int len) throws IOException {
			return read(b, off, len, false);
		}

		public long skip(long len) throws IOException {
			long remain = len;
			while(remain > 0){
				if(blkmode){
					if(pos == end){
						refill();
					}
					if(end < 0){
						break;
					}
					int nread = (int)Math.min(remain, end - pos);
					remain -= nread;
					pos += nread;
				}else{
					int nread = (int)Math.min(remain, MAX_BLOCK_SIZE);
					if((nread = in.read(buf, 0, nread)) < 0){
						break;
					}
					remain -= nread;
				}
			}
			return len - remain;
		}

		public int available() throws IOException {
			if(blkmode){
				if((pos == end) && (unread == 0)){
					int n;
					while((n = readBlockHeader(false)) == 0)
						;
					switch(n){
					case HEADER_BLOCKED:
						break;

					case -1:
						pos = 0;
						end = -1;
						break;

					default:
						pos = 0;
						end = 0;
						unread = n;
						break;
					}
				}
				// avoid unnecessary call to in.available() if possible
				int unreadAvail = (unread > 0) ? Math.min(in.available(), unread) : 0;
				return (end >= 0) ? (end - pos) + unreadAvail : 0;
			}else{
				return in.available();
			}
		}

		public void close() throws IOException {
			if(blkmode){
				pos = 0;
				end = -1;
				unread = 0;
			}
			in.close();
		}

		/**
		 * Attempts to read len bytes into byte array b at offset off. Returns
		 * the number of bytes read, or -1 if the end of stream/block data has
		 * been reached. If copy is true, reads values into an intermediate
		 * buffer before copying them to b (to avoid exposing a reference to b).
		 */
		int read(byte[] b, int off, int len, boolean copy) throws IOException {
			if(len == 0){
				return 0;
			}else if(blkmode){
				if(pos == end){
					refill();
				}
				if(end < 0){
					return -1;
				}
				int nread = Math.min(len, end - pos);
				System.arraycopy(buf, pos, b, off, nread);
				pos += nread;
				return nread;
			}else if(copy){
				int nread = in.read(buf, 0, Math.min(len, MAX_BLOCK_SIZE));
				if(nread > 0){
					System.arraycopy(buf, 0, b, off, nread);
				}
				return nread;
			}else{
				return in.read(b, off, len);
			}
		}

		/* ----------------- primitive data input methods ------------------ */
		/*
		 * The following methods are equivalent to their counterparts in
		 * DataInputStream, except that they interpret data block boundaries and
		 * read the requested data from within data blocks when in block data
		 * mode.
		 */

		public void readFully(byte[] b) throws IOException {
			readFully(b, 0, b.length, false);
		}

		public void readFully(byte[] b, int off, int len) throws IOException {
			readFully(b, off, len, false);
		}

		public void readFully(byte[] b, int off, int len, boolean copy) throws IOException {
			while(len > 0){
				int n = read(b, off, len, copy);
				if(n < 0){
					throw new EOFException();
				}
				off += n;
				len -= n;
			}
		}

		public int skipBytes(int n) throws IOException {
			return din.skipBytes(n);
		}

		public boolean readBoolean() throws IOException {
			int v = read();
			if(v < 0){
				throw new EOFException();
			}
			return (v != 0);
		}

		public byte readByte() throws IOException {
			int v = read();
			if(v < 0){
				throw new EOFException();
			}
			return (byte)v;
		}

		public int readUnsignedByte() throws IOException {
			int v = read();
			if(v < 0){
				throw new EOFException();
			}
			return v;
		}

		public char readChar() throws IOException {
			if(!blkmode){
				pos = 0;
				in.readFully(buf, 0, 2);
			}else if(end - pos < 2){
				return din.readChar();
			}
			char v = Bits.getChar(buf, pos);
			pos += 2;
			return v;
		}

		public short readShort() throws IOException {
			if(!blkmode){
				pos = 0;
				in.readFully(buf, 0, 2);
			}else if(end - pos < 2){
				return din.readShort();
			}
			short v = Bits.getShort(buf, pos);
			pos += 2;
			return v;
		}

		public int readUnsignedShort() throws IOException {
			if(!blkmode){
				pos = 0;
				in.readFully(buf, 0, 2);
			}else if(end - pos < 2){
				return din.readUnsignedShort();
			}
			int v = Bits.getShort(buf, pos) & 0xFFFF;
			pos += 2;
			return v;
		}

		public int readInt() throws IOException {
			if(!blkmode){
				pos = 0;
				in.readFully(buf, 0, 4);
			}else if(end - pos < 4){
				return din.readInt();
			}
			int v = Bits.getInt(buf, pos);
			pos += 4;
			return v;
		}

		public float readFloat() throws IOException {
			if(!blkmode){
				pos = 0;
				in.readFully(buf, 0, 4);
			}else if(end - pos < 4){
				return din.readFloat();
			}
			float v = Bits.getFloat(buf, pos);
			pos += 4;
			return v;
		}

		public long readLong() throws IOException {
			if(!blkmode){
				pos = 0;
				in.readFully(buf, 0, 8);
			}else if(end - pos < 8){
				return din.readLong();
			}
			long v = Bits.getLong(buf, pos);
			pos += 8;
			return v;
		}

		public double readDouble() throws IOException {
			if(!blkmode){
				pos = 0;
				in.readFully(buf, 0, 8);
			}else if(end - pos < 8){
				return din.readDouble();
			}
			double v = Bits.getDouble(buf, pos);
			pos += 8;
			return v;
		}

		public String readUTF() throws IOException {
			return readUTFBody(readUnsignedShort());
		}

		@SuppressWarnings("deprecation")
		public String readLine() throws IOException {
			return din.readLine(); // deprecated, not worth optimizing
		}

		/* -------------- primitive data array input methods --------------- */
		/*
		 * The following methods read in spans of primitive data values. Though
		 * equivalent to calling the corresponding primitive read methods
		 * repeatedly, these methods are optimized for reading groups of
		 * primitive data values more efficiently.
		 */

		void readBooleans(boolean[] v, int off, int len) throws IOException {
			int stop, endoff = off + len;
			while(off < endoff){
				if(!blkmode){
					int span = Math.min(endoff - off, MAX_BLOCK_SIZE);
					in.readFully(buf, 0, span);
					stop = off + span;
					pos = 0;
				}else if(end - pos < 1){
					v[off++] = din.readBoolean();
					continue;
				}else{
					stop = Math.min(endoff, off + end - pos);
				}

				while(off < stop){
					v[off++] = Bits.getBoolean(buf, pos++);
				}
			}
		}

		void readChars(char[] v, int off, int len) throws IOException {
			int stop, endoff = off + len;
			while(off < endoff){
				if(!blkmode){
					int span = Math.min(endoff - off, MAX_BLOCK_SIZE >> 1);
					in.readFully(buf, 0, span << 1);
					stop = off + span;
					pos = 0;
				}else if(end - pos < 2){
					v[off++] = din.readChar();
					continue;
				}else{
					stop = Math.min(endoff, off + ((end - pos) >> 1));
				}

				while(off < stop){
					v[off++] = Bits.getChar(buf, pos);
					pos += 2;
				}
			}
		}

		void readShorts(short[] v, int off, int len) throws IOException {
			int stop, endoff = off + len;
			while(off < endoff){
				if(!blkmode){
					int span = Math.min(endoff - off, MAX_BLOCK_SIZE >> 1);
					in.readFully(buf, 0, span << 1);
					stop = off + span;
					pos = 0;
				}else if(end - pos < 2){
					v[off++] = din.readShort();
					continue;
				}else{
					stop = Math.min(endoff, off + ((end - pos) >> 1));
				}

				while(off < stop){
					v[off++] = Bits.getShort(buf, pos);
					pos += 2;
				}
			}
		}

		void readInts(int[] v, int off, int len) throws IOException {
			int stop, endoff = off + len;
			while(off < endoff){
				if(!blkmode){
					int span = Math.min(endoff - off, MAX_BLOCK_SIZE >> 2);
					in.readFully(buf, 0, span << 2);
					stop = off + span;
					pos = 0;
				}else if(end - pos < 4){
					v[off++] = din.readInt();
					continue;
				}else{
					stop = Math.min(endoff, off + ((end - pos) >> 2));
				}

				while(off < stop){
					v[off++] = Bits.getInt(buf, pos);
					pos += 4;
				}
			}
		}

		void readFloats(float[] v, int off, int len) throws IOException {
			int span, endoff = off + len;
			while(off < endoff){
				if(!blkmode){
					span = Math.min(endoff - off, MAX_BLOCK_SIZE >> 2);
					in.readFully(buf, 0, span << 2);
					pos = 0;
				}else if(end - pos < 4){
					v[off++] = din.readFloat();
					continue;
				}else{
					span = Math.min(endoff - off, ((end - pos) >> 2));
				}

				bytesToFloats(buf, pos, v, off, span);
				off += span;
				pos += span << 2;
			}
		}

		void readLongs(long[] v, int off, int len) throws IOException {
			int stop, endoff = off + len;
			while(off < endoff){
				if(!blkmode){
					int span = Math.min(endoff - off, MAX_BLOCK_SIZE >> 3);
					in.readFully(buf, 0, span << 3);
					stop = off + span;
					pos = 0;
				}else if(end - pos < 8){
					v[off++] = din.readLong();
					continue;
				}else{
					stop = Math.min(endoff, off + ((end - pos) >> 3));
				}

				while(off < stop){
					v[off++] = Bits.getLong(buf, pos);
					pos += 8;
				}
			}
		}

		void readDoubles(double[] v, int off, int len) throws IOException {
			int span, endoff = off + len;
			while(off < endoff){
				if(!blkmode){
					span = Math.min(endoff - off, MAX_BLOCK_SIZE >> 3);
					in.readFully(buf, 0, span << 3);
					pos = 0;
				}else if(end - pos < 8){
					v[off++] = din.readDouble();
					continue;
				}else{
					span = Math.min(endoff - off, ((end - pos) >> 3));
				}

				bytesToDoubles(buf, pos, v, off, span);
				off += span;
				pos += span << 3;
			}
		}

		/**
		 * Reads in string written in "long" UTF format. "Long" UTF format is
		 * identical to standard UTF, except that it uses an 8 byte header
		 * (instead of the standard 2 bytes) to convey the UTF encoding length.
		 */
		String readLongUTF() throws IOException {
			return readUTFBody(readLong());
		}

		/**
		 * Reads in the "body" (i.e., the UTF representation minus the 2-byte or
		 * 8-byte length header) of a UTF encoding, which occupies the next
		 * utflen bytes.
		 */
		private String readUTFBody(long utflen) throws IOException {
			StringBuilder sbuf = new StringBuilder();
			if(!blkmode){
				end = pos = 0;
			}

			while(utflen > 0){
				int avail = end - pos;
				if(avail >= 3 || (long)avail == utflen){
					utflen -= readUTFSpan(sbuf, utflen);
				}else{
					if(blkmode){
						// near block boundary, read one byte at a time
						utflen -= readUTFChar(sbuf, utflen);
					}else{
						// shift and refill buffer manually
						if(avail > 0){
							System.arraycopy(buf, pos, buf, 0, avail);
						}
						pos = 0;
						end = (int)Math.min(MAX_BLOCK_SIZE, utflen);
						in.readFully(buf, avail, end - avail);
					}
				}
			}

			return sbuf.toString();
		}

		/**
		 * Reads span of UTF-encoded characters out of internal buffer (starting
		 * at offset pos and ending at or before offset end), consuming no more
		 * than utflen bytes. Appends read characters to sbuf. Returns the
		 * number of bytes consumed.
		 */
		private long readUTFSpan(StringBuilder sbuf, long utflen) throws IOException {
			int cpos = 0;
			int start = pos;
			int avail = Math.min(end - pos, CHAR_BUF_SIZE);
			// stop short of last char unless all of utf bytes in buffer
			int stop = pos + ((utflen > avail) ? avail - 2 : (int)utflen);
			boolean outOfBounds = false;

			try{
				while(pos < stop){
					int b1, b2, b3;
					b1 = buf[pos++] & 0xFF;
					switch(b1 >> 4){
					case 0:
					case 1:
					case 2:
					case 3:
					case 4:
					case 5:
					case 6:
					case 7: // 1 byte format: 0xxxxxxx
						cbuf[cpos++] = (char)b1;
						break;

					case 12:
					case 13: // 2 byte format: 110xxxxx 10xxxxxx
						b2 = buf[pos++];
						if((b2 & 0xC0) != 0x80){
							throw new UTFDataFormatException();
						}
						cbuf[cpos++] = (char)(((b1 & 0x1F) << 6) | ((b2 & 0x3F) << 0));
						break;

					case 14: // 3 byte format: 1110xxxx 10xxxxxx 10xxxxxx
						b3 = buf[pos + 1];
						b2 = buf[pos + 0];
						pos += 2;
						if((b2 & 0xC0) != 0x80 || (b3 & 0xC0) != 0x80){
							throw new UTFDataFormatException();
						}
						cbuf[cpos++] = (char)(((b1 & 0x0F) << 12) | ((b2 & 0x3F) << 6)
							| ((b3 & 0x3F) << 0));
						break;

					default: // 10xx xxxx, 1111 xxxx
						throw new UTFDataFormatException();
					}
				}
			}catch(ArrayIndexOutOfBoundsException ex){
				outOfBounds = true;
			}finally{
				if(outOfBounds || (pos - start) > utflen){
					/*
					 * Fix for 4450867: if a malformed utf char causes the
					 * conversion loop to scan past the expected end of the utf
					 * string, only consume the expected number of utf bytes.
					 */
					pos = start + (int)utflen;
					throw new UTFDataFormatException();
				}
			}

			sbuf.append(cbuf, 0, cpos);
			return pos - start;
		}

		/**
		 * Reads in single UTF-encoded character one byte at a time, appends the
		 * character to sbuf, and returns the number of bytes consumed. This
		 * method is used when reading in UTF strings written in block data mode
		 * to handle UTF-encoded characters which (potentially) straddle
		 * block-data boundaries.
		 */
		private int readUTFChar(StringBuilder sbuf, long utflen) throws IOException {
			int b1, b2, b3;
			b1 = readByte() & 0xFF;
			switch(b1 >> 4){
			case 0:
			case 1:
			case 2:
			case 3:
			case 4:
			case 5:
			case 6:
			case 7: // 1 byte format: 0xxxxxxx
				sbuf.append((char)b1);
				return 1;

			case 12:
			case 13: // 2 byte format: 110xxxxx 10xxxxxx
				if(utflen < 2){
					throw new UTFDataFormatException();
				}
				b2 = readByte();
				if((b2 & 0xC0) != 0x80){
					throw new UTFDataFormatException();
				}
				sbuf.append((char)(((b1 & 0x1F) << 6) | ((b2 & 0x3F) << 0)));
				return 2;

			case 14: // 3 byte format: 1110xxxx 10xxxxxx 10xxxxxx
				if(utflen < 3){
					if(utflen == 2){
						readByte(); // consume remaining byte
					}
					throw new UTFDataFormatException();
				}
				b2 = readByte();
				b3 = readByte();
				if((b2 & 0xC0) != 0x80 || (b3 & 0xC0) != 0x80){
					throw new UTFDataFormatException();
				}
				sbuf.append((char)(((b1 & 0x0F) << 12) | ((b2 & 0x3F) << 6) | ((b3 & 0x3F) << 0)));
				return 3;

			default: // 10xx xxxx, 1111 xxxx
				throw new UTFDataFormatException();
			}
		}

		/**
		 * Returns the number of bytes read from the input stream.
		 * 
		 * @return the number of bytes read from the input stream
		 */
		long getBytesRead() {
			return in.getBytesRead();
		}
	}

	/**
	 * Unsynchronized table which tracks wire handle to object mappings, as well
	 * as ClassNotFoundExceptions associated with deserialized objects. This
	 * class implements an exception-propagation algorithm for determining which
	 * objects should have ClassNotFoundExceptions associated with them, taking
	 * into account cycles and discontinuities (e.g., skipped fields) in the
	 * object graph.
	 *
	 * <p>
	 * General use of the table is as follows: during deserialization, a given
	 * object is first assigned a handle by calling the assign method. This
	 * method leaves the assigned handle in an "open" state, wherein
	 * dependencies on the exception status of other handles can be registered
	 * by calling the markDependency method, or an exception can be directly
	 * associated with the handle by calling markException. When a handle is
	 * tagged with an exception, the HandleTable assumes responsibility for
	 * propagating the exception to any other objects which depend
	 * (transitively) on the exception-tagged object.
	 *
	 * <p>
	 * Once all exception information/dependencies for the handle have been
	 * registered, the handle should be "closed" by calling the finish method on
	 * it. The act of finishing a handle allows the exception propagation
	 * algorithm to aggressively prune dependency links, lessening the
	 * performance/memory impact of exception tracking.
	 *
	 * <p>
	 * Note that the exception propagation algorithm used depends on handles
	 * being assigned/finished in LIFO order; however, for simplicity as well as
	 * memory conservation, it does not enforce this constraint.
	 */
	// REMIND: add full description of exception propagation algorithm?
	private static class HandleTable {

		/* status codes indicating whether object has associated exception */
		private static final byte STATUS_OK = 1;
		private static final byte STATUS_UNKNOWN = 2;
		private static final byte STATUS_EXCEPTION = 3;

		/** array mapping handle -> object status */
		byte[] status;
		/** array mapping handle -> object/exception (depending on status) */
		Object[] entries;
		/** array mapping handle -> list of dependent handles (if any) */
		HandleList[] deps;
		/** lowest unresolved dependency */
		int lowDep = -1;
		/** number of handles in table */
		int size = 0;

		/**
		 * Creates handle table with the given initial capacity.
		 */
		HandleTable(int initialCapacity) {
			status = new byte[initialCapacity];
			entries = new Object[initialCapacity];
			deps = new HandleList[initialCapacity];
		}

		/**
		 * Assigns next available handle to given object, and returns assigned
		 * handle. Once object has been completely deserialized (and all
		 * dependencies on other objects identified), the handle should be
		 * "closed" by passing it to finish().
		 */
		int assign(Object obj) {
			if(size >= entries.length){
				grow();
			}
			status[size] = STATUS_UNKNOWN;
			entries[size] = obj;
			return size++;
		}

		/**
		 * Registers a dependency (in exception status) of one handle on
		 * another. The dependent handle must be "open" (i.e., assigned, but not
		 * finished yet). No action is taken if either dependent or target
		 * handle is NULL_HANDLE.
		 */
		void markDependency(int dependent, int target) {
			if(dependent == NULL_HANDLE || target == NULL_HANDLE){
				return;
			}
			switch(status[dependent]){

			case STATUS_UNKNOWN:
				switch(status[target]){
				case STATUS_OK:
					// ignore dependencies on objs with no exception
					break;

				case STATUS_EXCEPTION:
					// eagerly propagate exception
					markException(dependent, (ClassNotFoundException)entries[target]);
					break;

				case STATUS_UNKNOWN:
					// add to dependency list of target
					if(deps[target] == null){
						deps[target] = new HandleList();
					}
					deps[target].add(dependent);

					// remember lowest unresolved target seen
					if(lowDep < 0 || lowDep > target){
						lowDep = target;
					}
					break;

				default:
					throw new InternalError();
				}
				break;

			case STATUS_EXCEPTION:
				break;

			default:
				throw new InternalError();
			}
		}

		/**
		 * Associates a ClassNotFoundException (if one not already associated)
		 * with the currently active handle and propagates it to other
		 * referencing objects as appropriate. The specified handle must be
		 * "open" (i.e., assigned, but not finished yet).
		 */
		void markException(int handle, ClassNotFoundException ex) {
			switch(status[handle]){
			case STATUS_UNKNOWN:
				status[handle] = STATUS_EXCEPTION;
				entries[handle] = ex;

				// propagate exception to dependents
				HandleList dlist = deps[handle];
				if(dlist != null){
					int ndeps = dlist.size();
					for(int i = 0; i < ndeps; i++){
						markException(dlist.get(i), ex);
					}
					deps[handle] = null;
				}
				break;

			case STATUS_EXCEPTION:
				break;

			default:
				throw new InternalError();
			}
		}

		/**
		 * Marks given handle as finished, meaning that no new dependencies will
		 * be marked for handle. Calls to the assign and finish methods must
		 * occur in LIFO order.
		 */
		void finish(int handle) {
			int end;
			if(lowDep < 0){
				// no pending unknowns, only resolve current handle
				end = handle + 1;
			}else if(lowDep >= handle){
				// pending unknowns now clearable, resolve all upward handles
				end = size;
				lowDep = -1;
			}else{
				// unresolved backrefs present, can't resolve anything yet
				return;
			}

			// change STATUS_UNKNOWN -> STATUS_OK in selected span of handles
			for(int i = handle; i < end; i++){
				switch(status[i]){
				case STATUS_UNKNOWN:
					status[i] = STATUS_OK;
					deps[i] = null;
					break;

				case STATUS_OK:
				case STATUS_EXCEPTION:
					break;

				default:
					throw new InternalError();
				}
			}
		}

		/**
		 * Assigns a new object to the given handle. The object previously
		 * associated with the handle is forgotten. This method has no effect if
		 * the given handle already has an exception associated with it. This
		 * method may be called at any time after the handle is assigned.
		 */
		void setObject(int handle, Object obj) {
			switch(status[handle]){
			case STATUS_UNKNOWN:
			case STATUS_OK:
				entries[handle] = obj;
				break;

			case STATUS_EXCEPTION:
				break;

			default:
				throw new InternalError();
			}
		}

		/**
		 * Looks up and returns object associated with the given handle. Returns
		 * null if the given handle is NULL_HANDLE, or if it has an associated
		 * ClassNotFoundException.
		 */
		Object lookupObject(int handle) {
			return (handle != NULL_HANDLE && status[handle] != STATUS_EXCEPTION) ? entries[handle]
				: null;
		}

		/**
		 * Looks up and returns ClassNotFoundException associated with the given
		 * handle. Returns null if the given handle is NULL_HANDLE, or if there
		 * is no ClassNotFoundException associated with the handle.
		 */
		ClassNotFoundException lookupException(int handle) {
			return (handle != NULL_HANDLE && status[handle] == STATUS_EXCEPTION)
				? (ClassNotFoundException)entries[handle]
				: null;
		}

		/**
		 * Resets table to its initial state.
		 */
		void clear() {
			Arrays.fill(status, 0, size, (byte)0);
			Arrays.fill(entries, 0, size, null);
			Arrays.fill(deps, 0, size, null);
			lowDep = -1;
			size = 0;
		}

		/**
		 * Returns number of handles registered in table.
		 */
		int size() {
			return size;
		}

		/**
		 * Expands capacity of internal arrays.
		 */
		private void grow() {
			int newCapacity = (entries.length << 1) + 1;

			byte[] newStatus = new byte[newCapacity];
			Object[] newEntries = new Object[newCapacity];
			HandleList[] newDeps = new HandleList[newCapacity];

			System.arraycopy(status, 0, newStatus, 0, size);
			System.arraycopy(entries, 0, newEntries, 0, size);
			System.arraycopy(deps, 0, newDeps, 0, size);

			status = newStatus;
			entries = newEntries;
			deps = newDeps;
		}

		/**
		 * Simple growable list of (integer) handles.
		 */
		private static class HandleList {
			private int[] list = new int[4];
			private int size = 0;

			public HandleList() {
			}

			public void add(int handle) {
				if(size >= list.length){
					int[] newList = new int[list.length << 1];
					System.arraycopy(list, 0, newList, 0, list.length);
					list = newList;
				}
				list[size++] = handle;
			}

			public int get(int index) {
				if(index >= size){
					throw new ArrayIndexOutOfBoundsException();
				}
				return list[index];
			}

			public int size() {
				return size;
			}
		}
	}

	/**
	 * Method for cloning arrays in case of using unsharing reading
	 */
	private static Object cloneArray(Object array) {
		if(array instanceof Object[]){
			return ((Object[])array).clone();
		}else if(array instanceof boolean[]){
			return ((boolean[])array).clone();
		}else if(array instanceof byte[]){
			return ((byte[])array).clone();
		}else if(array instanceof char[]){
			return ((char[])array).clone();
		}else if(array instanceof double[]){
			return ((double[])array).clone();
		}else if(array instanceof float[]){
			return ((float[])array).clone();
		}else if(array instanceof int[]){
			return ((int[])array).clone();
		}else if(array instanceof long[]){
			return ((long[])array).clone();
		}else if(array instanceof short[]){
			return ((short[])array).clone();
		}else{
			throw new AssertionError();
		}
	}

	// controlled access to ObjectStreamClassValidator
	private volatile ObjectStreamClassValidator validator;

	private static void setValidator(JdkInputStream ois, ObjectStreamClassValidator validator) {
		ois.validator = validator;
	}

	// static{
	// SharedSecrets.setJavaObjectInputStreamAccess(JdkInputStream::setValidator);
	// }

	public void defaultReadObject() throws IOException, ClassNotFoundException {
		SerialCallbackContext ctx = curContext;
		if(ctx == null){
			throw new NotActiveException("not in call to readObject");
		}
		Object curObj = ctx.getObj();
		ObjectStreamDesc curDesc = ctx.getDesc();
		bin.setBlockDataMode(false);
		defaultReadFields(curObj, curDesc);
		bin.setBlockDataMode(true);
		if(!curDesc.hasWriteObjectData()){
			/*
			 * Fix for 4360508: since stream does not contain terminating
			 * TC_ENDBLOCKDATA tag, set flag so that reading code elsewhere
			 * knows to simulate end-of-custom-data behavior.
			 */
			defaultDataEnd = true;
		}
		ClassNotFoundException ex = handles.lookupException(passHandle);
		if(ex != null){
			throw ex;
		}
	}
}
