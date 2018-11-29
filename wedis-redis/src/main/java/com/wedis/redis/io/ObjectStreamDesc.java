package com.wedis.redis.io;

import java.io.IOException;
import java.io.InvalidClassException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamConstants;
import java.io.Serializable;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.security.AccessController;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;

import com.wedis.base.util.UnsafeUtil;
import com.wedis.redis.io.ObjectStreamField.VT;

import sun.reflect.ReflectionFactory;

public class ObjectStreamDesc {

	/** name of class represented by this descriptor */
	private String name;
	/** class associated with this descriptor (if any) */
	private Class<?> cl;
	/** serialVersionUID of represented class (null if not computed yet) */
	private volatile Long suid;
	/** true if represents dynamic proxy class */
	private boolean isProxy;
	/** true if desc has data written by class-defined writeObject method */
	private boolean hasWriteObjectData;
	/** class-defined writeObject method, or null if none */
	private Method writeObjectMethod;
	/** class-defined readObject method, or null if none */
	private Method readObjectMethod;
	/** serialization-appropriate constructor, or null if none */
	private Constructor<?> cons;
	/** class-defined readObjectNoData method, or null if none */
	private Method readObjectNoDataMethod;
	/** true if exist Class */
	private boolean hasClass = true;
	/**
	 * true if desc has externalizable data written in block data format; this
	 * must be true by default to accommodate ObjectInputStream subclasses which
	 * override readClassDescriptor() to return class descriptors obtained from
	 * ObjectStreamClass.lookup() (see 4461737)
	 */
	private boolean hasBlockExternalData = true;
	/** true if represented class implements Externalizable */
	private boolean externalizable;
	/** true if represented class implements Serializable */
	private boolean serializable;
	/** true if represents enum type */
	private boolean isEnum;

	/** serialPersistentFields value indicating no serializable fields */
	public static final ObjectStreamField[] NO_FIELDS = new ObjectStreamField[0];
	/** serializable fields */
	private ObjectStreamField[] fields;

	/** aggregate marshalled size of primitive fields */
	private int primDataSize;
	/** number of primitive fields */
	private int numPrimFields;
	/** number of non-primitive fields */
	private int numObjFields;

	/** superclass descriptor appearing in stream */
	private ObjectStreamDesc superDesc;
	/** true if, and only if, the object has been correctly initialized */
	private boolean initialized;
	private static final ReflectionFactory reflFactory = AccessController
		.doPrivileged(new ReflectionFactory.GetReflectionFactoryAction());

	private static final VT<Boolean> booleanVT = new VT<Boolean>() {
		@Override
		public void setValue(Object obj, long key, Boolean value) {
			UnsafeUtil.unsafe.putBoolean(obj, key, value);
		}
	};
	private static final VT<Byte> byteVT = new VT<Byte>() {
		@Override
		public void setValue(Object obj, long key, Byte value) {
			UnsafeUtil.unsafe.putByte(obj, key, value);
		}
	};
	private static final VT<Character> charVT = new VT<Character>() {
		@Override
		public void setValue(Object obj, long key, Character value) {
			UnsafeUtil.unsafe.putChar(obj, key, value);
		}
	};
	private static final VT<Short> shortVT = new VT<Short>() {
		@Override
		public void setValue(Object obj, long key, Short value) {
			UnsafeUtil.unsafe.putShort(obj, key, value);
		}
	};
	private static final VT<Integer> intVT = new VT<Integer>() {
		@Override
		public void setValue(Object obj, long key, Integer value) {
			UnsafeUtil.unsafe.putInt(obj, key, value);
		}
	};
	private static final VT<Long> longVT = new VT<Long>() {
		@Override
		public void setValue(Object obj, long key, Long value) {
			UnsafeUtil.unsafe.putLong(obj, key, value);
		}
	};
	private static final VT<Float> floatVT = new VT<Float>() {
		@Override
		public void setValue(Object obj, long key, Float value) {
			UnsafeUtil.unsafe.putFloat(obj, key, value);
		}
	};
	private static final VT<Double> doubleVT = new VT<Double>() {
		@Override
		public void setValue(Object obj, long key, Double value) {
			UnsafeUtil.unsafe.putDouble(obj, key, value);
		}
	};
	private static final VT<Object> objectVT = new VT<Object>() {
		@Override
		public void setValue(Object obj, long key, Object value) {
			UnsafeUtil.unsafe.putObject(obj, key, value);
		}
	};

	void readNonProxy(JdkInputStream in) throws IOException {
		name = in.readUTF();
		suid = Long.valueOf(in.readLong());
		isProxy = false;

		byte flags = in.readByte();
		hasWriteObjectData = ((flags & ObjectStreamConstants.SC_WRITE_METHOD) != 0);
		hasBlockExternalData = ((flags & ObjectStreamConstants.SC_BLOCK_DATA) != 0);
		externalizable = ((flags & ObjectStreamConstants.SC_EXTERNALIZABLE) != 0);
		boolean sflag = ((flags & ObjectStreamConstants.SC_SERIALIZABLE) != 0);
		if(externalizable && sflag){
			throw new InvalidClassException(name, "serializable and externalizable flags conflict");
		}
		serializable = externalizable || sflag;
		isEnum = ((flags & ObjectStreamConstants.SC_ENUM) != 0);
		if(isEnum && suid.longValue() != 0L){
			throw new InvalidClassException(name,
				"enum descriptor has non-zero serialVersionUID: " + suid);
		}

		int numFields = in.readShort();
		if(isEnum && numFields != 0){
			throw new InvalidClassException(name,
				"enum descriptor has non-zero field count: " + numFields);
		}
		fields = (numFields > 0) ? new ObjectStreamField[numFields] : NO_FIELDS;
		for(int i = 0; i < numFields; i++){
			char tcode = (char)in.readByte();
			String fname = in.readUTF();
			String signature = ((tcode == 'L') || (tcode == '[')) ? in.readTypeString()
				: new String(new char[]{tcode});
			try{
				fields[i] = new ObjectStreamField(fname, signature, false);
			}catch(RuntimeException e){
				throw (IOException)new InvalidClassException(name,
					"invalid descriptor for field " + fname).initCause(e);
			}
		}
		computeFieldOffsets();
	}

	private void computeFieldOffsets() throws InvalidClassException {
		primDataSize = 0;
		numObjFields = 0;
		int firstObjIndex = -1;

		for(int i = 0; i < fields.length; i++){
			ObjectStreamField f = fields[i];
			switch(f.getTypeCode()){
			case 'Z':
			case 'B':
				f.setOffset(primDataSize++);
				break;

			case 'C':
			case 'S':
				f.setOffset(primDataSize);
				primDataSize += 2;
				break;

			case 'I':
			case 'F':
				f.setOffset(primDataSize);
				primDataSize += 4;
				break;

			case 'J':
			case 'D':
				f.setOffset(primDataSize);
				primDataSize += 8;
				break;

			case '[':
			case 'L':
				f.setOffset(numObjFields++);
				if(firstObjIndex == -1){
					firstObjIndex = i;
				}
				break;

			default:
				throw new InternalError();
			}
		}
		if(firstObjIndex != -1 && firstObjIndex + numObjFields != fields.length){
			throw new InvalidClassException(name, "illegal field order");
		}
		
		numPrimFields = fields.length - numObjFields;
	}

	void initNonProxy(ObjectStreamDesc model, Class<?> cl, ObjectStreamDesc superDesc) {
		long suid = Long.valueOf(model.getSerialVersionUID());

		this.superDesc = superDesc;
		name = model.name;
		this.cl = cl;
		this.suid = suid;
		isProxy = false;
		isEnum = model.isEnum;
		serializable = model.serializable;
		externalizable = model.externalizable;
		hasBlockExternalData = model.hasBlockExternalData;
		hasWriteObjectData = model.hasWriteObjectData;
		fields = model.fields;
		primDataSize = model.primDataSize;
		numPrimFields = model.numPrimFields;
		numObjFields = model.numObjFields;
		hasClass = model.hasClass;

		if(!externalizable && hasClass){
			cons = getSerializableConstructor(cl);
			writeObjectMethod = getPrivateMethod(cl, "writeObject",
				new Class<?>[]{ObjectOutputStream.class}, Void.TYPE);
			readObjectMethod = getPrivateMethod(cl, "readObject",
				new Class<?>[]{ObjectInputStream.class}, Void.TYPE);
			readObjectNoDataMethod = getPrivateMethod(cl, "readObjectNoData", null, Void.TYPE);
			hasWriteObjectData = (writeObjectMethod != null);
		}

		for(ObjectStreamField f : fields){
			f.setField(resolveField(f.getName()));
		}

		initialized = true;
	}

	private Field resolveField(String fname) {
		if(hasClass){
			// class not found
			try{
				Field field = cl.getDeclaredField(fname);
				field.setAccessible(true);
				return field;
			}catch(Exception e){
				return null;
			}
		}
		return null;
	}

	private static Constructor<?> getSerializableConstructor(Class<?> cl) {
		Class<?> initCl = cl;
		while(Serializable.class.isAssignableFrom(initCl)){
			if((initCl = initCl.getSuperclass()) == null){
				return null;
			}
		}
		try{
			Constructor<?> cons = initCl.getDeclaredConstructor((Class<?>[])null);
			cons = reflFactory.newConstructorForSerialization(cl, cons);
			cons.setAccessible(true);
			return cons;
		}catch(NoSuchMethodException ex){
			return null;
		}
	}

	private static Method getPrivateMethod(Class<?> cl, String name, Class<?>[] argTypes,
		Class<?> returnType) {
		try{
			Method meth = cl.getDeclaredMethod(name, argTypes);
			meth.setAccessible(true);
			int mods = meth.getModifiers();
			return ((meth.getReturnType() == returnType) && ((mods & Modifier.STATIC) == 0)
				&& ((mods & Modifier.PRIVATE) != 0)) ? meth : null;
		}catch(NoSuchMethodException ex){
			return null;
		}
	}

	void initProxy(ObjectStreamDesc superDesc) throws InvalidClassException {
		this.superDesc = superDesc;
		isProxy = true;
		serializable = true;
		suid = Long.valueOf(0);
		fields = NO_FIELDS;
		initialized = true;
	}

	public long getSerialVersionUID() {
		return suid.longValue();
	}

	boolean isExternalizable() {
		requireInitialized();
		return externalizable;
	}

	private final void requireInitialized() {
		if(!initialized)
			throw new InternalError("Unexpected call when not initialized");
	}

	boolean hasWriteObjectData() {
		requireInitialized();
		return hasWriteObjectData;
	}

	int getPrimDataSize() {
		return primDataSize;
	}

	void setPrimFieldValues(Object obj, byte[] buf) {
		if(obj == null){
			throw new NullPointerException();
		}
		for(int i = 0; i < numPrimFields; i++){
			ObjectStreamField field = fields[i];
			int off = field.getOffset();
			switch(field.getTypeCode()){
			case 'Z':
				field.setValue(obj, Bits.getBoolean(buf, off), hasClass, booleanVT);
				break;
			case 'B':
				field.setValue(obj, buf[off], hasClass, byteVT);
				break;
			case 'C':
				field.setValue(obj, Bits.getChar(buf, off), hasClass, charVT);
				break;
			case 'S':
				field.setValue(obj, Bits.getShort(buf, off), hasClass, shortVT);
				break;
			case 'I':
				field.setValue(obj, Bits.getInt(buf, off), hasClass, intVT);
				break;
			case 'F':
				field.setValue(obj, Bits.getFloat(buf, off), hasClass, floatVT);
				break;
			case 'J':
				field.setValue(obj, Bits.getLong(buf, off), hasClass, longVT);
				break;
			case 'D':
				field.setValue(obj, Bits.getDouble(buf, off), hasClass, doubleVT);
				break;
			default:
				throw new InternalError();
			}
		}
	}

	void setObjFieldValues(Object obj, Object[] vals) {
		if(obj == null){
			throw new NullPointerException();
		}
		for(int i = numPrimFields; i < fields.length; i++){
			ObjectStreamField field = fields[i];
			switch(field.getTypeCode()){
			case 'L':
			case '[':
				field.setValue(obj, vals[field.getOffset()], hasClass, objectVT);
				break;

			default:
				throw new InternalError();
			}
		}
	}

	ObjectStreamField[] getFields(boolean copy) {
		return copy ? fields.clone() : fields;
	}

	int getNumObjFields() {
		return numObjFields;
	}

	public String getName() {
		return name;
	}

	boolean isEnum() {
		requireInitialized();
		return isEnum;
	}

	Object newInstance() {
		requireInitialized();
		if(cons != null){
			try{
				return cons.newInstance();
			}catch(Exception e){
				return new HashMap<>();
			}
		}else{
			return new HashMap<>();
		}
	}

	void hasClass(boolean hasClass) {
		this.hasClass = hasClass;
	}

	boolean hasReadObjectMethod() {
		requireInitialized();
		return (readObjectMethod != null);
	}

	void invokeReadObject(Object obj, ObjectInputStream in)
		throws ClassNotFoundException, IOException, UnsupportedOperationException {
		requireInitialized();
		if(readObjectMethod != null){
			try{
				readObjectMethod.invoke(obj, new Object[]{in});
			}catch(InvocationTargetException ex){
				Throwable th = ex.getTargetException();
				if(th instanceof ClassNotFoundException){
					throw (ClassNotFoundException)th;
				}else if(th instanceof IOException){
					throw (IOException)th;
				}else{
					throwMiscException(th);
				}
			}catch(IllegalAccessException ex){
				// should not occur, as access checks have been suppressed
				throw new InternalError(ex);
			}
		}else{
			throw new UnsupportedOperationException();
		}
	}

	private static void throwMiscException(Throwable th) throws IOException {
		if(th instanceof RuntimeException){
			throw (RuntimeException)th;
		}else if(th instanceof Error){
			throw (Error)th;
		}else{
			IOException ex = new IOException("unexpected exception type");
			ex.initCause(th);
			throw ex;
		}
	}

	ObjectStreamDesc[] getClassDataLayout() throws InvalidClassException {
		ArrayList<ObjectStreamDesc> slots = new ArrayList<>();

		for(ObjectStreamDesc c = this; c != null; c = c.superDesc){
			c.hasClass(this.hasClass);
			slots.add(c);
		}

		// order slots from superclass -> subclass
		Collections.reverse(slots);
		return slots.toArray(new ObjectStreamDesc[slots.size()]);
	}
}
