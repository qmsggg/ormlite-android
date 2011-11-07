package com.j256.ormlite.android;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import com.j256.ormlite.db.DatabaseType;
import com.j256.ormlite.field.DataPersister;
import com.j256.ormlite.field.DataType;
import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.field.DatabaseFieldConfig;
import com.j256.ormlite.support.ConnectionSource;
import com.j256.ormlite.table.DatabaseTableConfig;

/**
 * Class which uses reflection to make the job of processing the {@link DatabaseField} annotation more efficient. In
 * current (as of 11/2011) versions of Android, Annotations are ghastly slow. This uses reflection on the Android
 * classes to work around this issue. Gross and a hack but a significant (~20x) performance improvement.
 * 
 * <p>
 * Thanks much go to Josh Guilfoyle for the idea and the code framework to make this happen.
 * </p>
 * 
 * @author joshguilfoyle, graywatson
 */
public class DatabaseTableConfigUtil {

	private static Class<?> annotationFactoryClazz;
	private static Field elementsField;
	private static Class<?> annotationMemberClazz;
	private static Field nameField;
	private static Field valueField;
	private static int workedC = 0;

	private static final int[] configFieldNums = lookupClasses();

	/**
	 * Build our list table config from a class using some annotation fu around.
	 */
	public static <T> DatabaseTableConfig<T> fromClass(ConnectionSource connectionSource, Class<T> clazz)
			throws SQLException {
		DatabaseType databaseType = connectionSource.getDatabaseType();
		String tableName = DatabaseTableConfig.extractTableName(clazz);
		List<DatabaseFieldConfig> fieldConfigs = new ArrayList<DatabaseFieldConfig>();
		for (Class<?> classWalk = clazz; classWalk != null; classWalk = classWalk.getSuperclass()) {
			for (Field field : classWalk.getDeclaredFields()) {
				DatabaseFieldConfig config = configFromField(databaseType, tableName, field);
				if (config != null && config.isPersisted()) {
					fieldConfigs.add(config);
				}
			}
		}
		if (fieldConfigs.size() == 0) {
			return null;
		} else {
			return new DatabaseTableConfig<T>(clazz, tableName, fieldConfigs);
		}
	}

	/**
	 * Return the number of fields configured using our reflection hack. This is for testing.
	 */
	public static int getWorkedC() {
		return workedC;
	}

	/**
	 * This does all of the class reflection fu to find our classes, find the order of field names, and construct our
	 * array of ConfigField entries the correspond to the AnnotationMember array.
	 */
	private static int[] lookupClasses() {
		Class<?> annotationMemberArrayClazz;
		try {
			annotationFactoryClazz = Class.forName("org.apache.harmony.lang.annotation.AnnotationFactory");
			annotationMemberClazz = Class.forName("org.apache.harmony.lang.annotation.AnnotationMember");
			annotationMemberArrayClazz = Class.forName("[Lorg.apache.harmony.lang.annotation.AnnotationMember;");
			annotationMemberClazz = Class.forName("org.apache.harmony.lang.annotation.AnnotationMember");
		} catch (ClassNotFoundException e) {
			return null;
		}

		Field fieldField;
		try {
			elementsField = annotationFactoryClazz.getDeclaredField("elements");
			elementsField.setAccessible(true);

			nameField = annotationMemberClazz.getDeclaredField("name");
			nameField.setAccessible(true);
			valueField = annotationMemberClazz.getDeclaredField("value");
			valueField.setAccessible(true);

			fieldField = DatabaseFieldSample.class.getDeclaredField("field");
		} catch (SecurityException e) {
			return null;
		} catch (NoSuchFieldException e) {
			return null;
		}

		DatabaseField databaseField = fieldField.getAnnotation(DatabaseField.class);
		InvocationHandler proxy = Proxy.getInvocationHandler(databaseField);
		if (proxy.getClass() != annotationFactoryClazz) {
			return null;
		}

		try {
			// this should be an array of AnnotationMember objects
			Object elements = elementsField.get(proxy);
			if (elements == null || elements.getClass() != annotationMemberArrayClazz) {
				return null;
			}

			Object[] elementArray = (Object[]) elements;
			int[] configNums = new int[elementArray.length];

			// build our array of field-numbers that match the AnnotationMember array
			for (int i = 0; i < elementArray.length; i++) {
				String name = (String) nameField.get(elementArray[i]);
				configNums[i] = configFieldNameToNum(name);
			}
			return configNums;
		} catch (IllegalAccessException e) {
			return null;
		}
	}

	/**
	 * Convert the name of the @DatabaseField fields into a number for easy processing later.
	 */
	private static int configFieldNameToNum(String configName) {
		if (configName.equals("columnName")) {
			return 1;
		} else if (configName.equals("dataType")) {
			return 2;
		} else if (configName.equals("defaultValue")) {
			return 3;
		} else if (configName.equals("width")) {
			return 4;
		} else if (configName.equals("canBeNull")) {
			return 5;
		} else if (configName.equals("id")) {
			return 6;
		} else if (configName.equals("generatedId")) {
			return 7;
		} else if (configName.equals("generatedIdSequence")) {
			return 8;
		} else if (configName.equals("foreign")) {
			return 9;
		} else if (configName.equals("useGetSet")) {
			return 10;
		} else if (configName.equals("unknownEnumName")) {
			return 11;
		} else if (configName.equals("throwIfNull")) {
			return 12;
		} else if (configName.equals("persisted")) {
			return 13;
		} else if (configName.equals("format")) {
			return 14;
		} else if (configName.equals("unique")) {
			return 15;
		} else if (configName.equals("uniqueCombo")) {
			return 16;
		} else if (configName.equals("index")) {
			return 17;
		} else if (configName.equals("uniqueIndex")) {
			return 18;
		} else if (configName.equals("indexName")) {
			return 19;
		} else if (configName.equals("uniqueIndexName")) {
			return 20;
		} else if (configName.equals("foreignAutoRefresh")) {
			return 21;
		} else if (configName.equals("maxForeignAutoRefreshLevel")) {
			return 22;
		} else if (configName.equals("persisterClass")) {
			return 23;
		} else if (configName.equals("allowGeneratedIdInsert")) {
			return 24;
		} else if (configName.equals("columnDefinition")) {
			return 25;
		} else if (configName.equals("foreignAutoCreate")) {
			return 26;
		} else if (configName.equals("version")) {
			return 27;
		} else {
			throw new IllegalStateException("Could not find support for DatabaseField " + configName);
		}
	}

	/**
	 * Extract our configuration information from the field by looking for a {@link DatabaseField} annotation.
	 */
	private static DatabaseFieldConfig configFromField(DatabaseType databaseType, String tableName, Field field)
			throws SQLException {

		if (configFieldNums == null) {
			return DatabaseFieldConfig.fromField(databaseType, tableName, field);
		}

		/*
		 * This, unfortunately, we can't get around. This creates a AnnotationFactory, an array of AnnotationMember
		 * fields, and possibly another array of AnnotationMember values. This creates a large number of GC'd objects.
		 */
		DatabaseField databaseField = field.getAnnotation(DatabaseField.class);
		if (databaseField == null) {
			return null;
		}

		DatabaseFieldConfig config = null;
		try {
			config = buildConfig(databaseField, tableName, field);
		} catch (Exception e) {
			// ignored so we will configure normally below
		}

		if (config == null) {
			return DatabaseFieldConfig.fromField(databaseType, tableName, field);
		} else {
			workedC++;
			return config;
		}
	}

	/**
	 * Instead of calling the annotation methods directly, we peer inside the proxy and investigate the array of
	 * AnnotationMember objects stored by the AnnotationFactory.
	 */
	private static DatabaseFieldConfig buildConfig(DatabaseField databaseField, String tableName, Field field)
			throws Exception {
		InvocationHandler proxy = Proxy.getInvocationHandler(databaseField);
		if (proxy.getClass() != annotationFactoryClazz) {
			return null;
		}
		// this should be an array of AnnotationMember objects
		Object elementsObject = elementsField.get(proxy);
		if (elementsObject == null) {
			return null;
		}
		DatabaseFieldConfig config = new DatabaseFieldConfig(field.getName());
		Object[] objs = (Object[]) elementsObject;
		for (int i = 0; i < configFieldNums.length; i++) {
			Object value = valueField.get(objs[i]);
			if (value != null) {
				assignConfigField(configFieldNums[i], config, field, value);
			}
		}
		return config;
	}

	/**
	 * Converts from field/value from the {@link DatabaseField} annotation to {@link DatabaseFieldConfig} values. This
	 * is very specific to this annotation.
	 */
	private static void assignConfigField(int configNum, DatabaseFieldConfig config, Field field, Object value) {
		switch (configNum) {
			case 1 :
				config.setColumnName(valueIfNotBlank((String) value));
				break;
			case 2 :
				config.setDataType((DataType) value);
				break;
			case 3 :
				String defaultValue = (String) value;
				if (!(defaultValue == null || defaultValue.equals(DatabaseField.DEFAULT_STRING))) {
					config.setDefaultValue(defaultValue);
				}
				break;
			case 4 :
				config.setWidth((Integer) value);
				break;
			case 5 :
				config.setCanBeNull((Boolean) value);
				break;
			case 6 :
				config.setId((Boolean) value);
				break;
			case 7 :
				config.setGeneratedId((Boolean) value);
				break;
			case 8 :
				config.setGeneratedIdSequence(valueIfNotBlank((String) value));
				break;
			case 9 :
				config.setForeign((Boolean) value);
				break;
			case 10 :
				config.setUseGetSet((Boolean) value);
				break;
			case 11 :
				config.setUnknownEnumValue(DatabaseFieldConfig.findMatchingEnumVal(field, (String) value));
				break;
			case 12 :
				config.setThrowIfNull((Boolean) value);
				break;
			case 13 :
				config.setPersisted((Boolean) value);
				break;
			case 14 :
				config.setFormat(valueIfNotBlank((String) value));
				break;
			case 15 :
				config.setUnique((Boolean) value);
				break;
			case 16 :
				config.setUniqueCombo((Boolean) value);
				break;
			case 17 :
				config.setIndex((Boolean) value);
				break;
			case 18 :
				config.setUniqueIndex((Boolean) value);
				break;
			case 19 :
				config.setIndexName(valueIfNotBlank((String) value));
				break;
			case 20 :
				config.setUniqueIndexName(valueIfNotBlank((String) value));
				break;
			case 21 :
				config.setForeignAutoRefresh((Boolean) value);
				break;
			case 22 :
				config.setMaxForeignAutoRefreshLevel((Integer) value);
				break;
			case 23 :
				@SuppressWarnings("unchecked")
				Class<? extends DataPersister> clazz = (Class<? extends DataPersister>) value;
				config.setPersisterClass(clazz);
				break;
			case 24 :
				config.setAllowGeneratedIdInsert((Boolean) value);
				break;
			case 25 :
				config.setColumnDefinition(valueIfNotBlank((String) value));
				break;
			case 26 :
				config.setForeignAutoCreate((Boolean) value);
				break;
			case 27 :
				config.setVersion((Boolean) value);
				break;
			default :
				throw new IllegalStateException("Could not find support for DatabaseField number " + configNum);
		}
	}

	private static String valueIfNotBlank(String value) {
		if (value == null || value.length() == 0) {
			return null;
		} else {
			return value;
		}
	}

	/**
	 * Class used to investigate the @DatabaseField annotation.
	 */
	private static class DatabaseFieldSample {
		@SuppressWarnings("unused")
		@DatabaseField
		String field;
	}
}
