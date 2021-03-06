package com.minyisoft.webapp.core.utils.mapper.json;

import java.io.IOException;

import lombok.Getter;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.type.TypeFactory;
import com.fasterxml.jackson.databind.util.JSONPObject;
import com.fasterxml.jackson.datatype.guava.GuavaModule;
import com.minyisoft.webapp.core.utils.mapper.json.jackson.ModelObjectDeserializerModifier;
import com.minyisoft.webapp.core.utils.mapper.json.jackson.ModelObjectSerializerModifier;

public enum JsonMapper {
	NON_EMPTY_MAPPER(_initMapper(Include.NON_EMPTY)), // 创建只输出非Null且非Empty(如List.isEmpty)的属性到Json字符串的Mapper,建议在外部接口中使用
	NON_DEFAULT_MAPPER(_initMapper(Include.NON_DEFAULT)), // 创建只输出初始值被改变的属性到Json字符串的Mapper,最节约的存储方式，建议在内部接口中使用
	DEFAULT_TYPEING_MAPPER(_initMapper(Include.NON_DEFAULT)) {
		@Override
		protected void furtherInitMapper() {
			getMapper().enableDefaultTypingAsProperty(ObjectMapper.DefaultTyping.OBJECT_AND_NON_CONCRETE, "@class");
		}
	}, // 保存类型信息，用于多态类型的转换
	MODEL_OBJECT_MAPPER(_initMapper(Include.NON_DEFAULT)) {
		@Override
		protected void furtherInitMapper() {
			getMapper().registerModules(new GuavaModule(), new SimpleModule() {
				private static final long serialVersionUID = 3919928191745949680L;

				public void setupModule(SetupContext context) {
					super.setupModule(context);
					context.addBeanSerializerModifier(new ModelObjectSerializerModifier());
					context.addBeanDeserializerModifier(new ModelObjectDeserializerModifier());
				}
			});
			// 转换json时只检查变量
			getMapper().setVisibilityChecker(
					getMapper().getSerializationConfig().getDefaultVisibilityChecker()
							.withFieldVisibility(Visibility.ANY).withGetterVisibility(Visibility.NONE)
							.withIsGetterVisibility(Visibility.NONE).withSetterVisibility(Visibility.NONE));
		}
	};// 创建用于序列化与反序列化IModelObject接口实现类，对使用了cglib增强的IModelObject
		// bean，序列化时只输出id值，反序列化时根据id重新构造cglib增强的对象

	private static Logger logger = LoggerFactory.getLogger(JsonMapper.class);

	@Getter
	private ObjectMapper mapper;

	private JsonMapper(ObjectMapper mapper) {
		this.mapper = mapper;
		furtherInitMapper();
	}

	private static ObjectMapper _initMapper(Include include) {
		ObjectMapper mapper = new ObjectMapper();
		// 设置输出时包含属性的风格
		if (include != null) {
			mapper.setSerializationInclusion(include);
		}
		// 设置输入时忽略在JSON字符串中存在但Java对象实际没有的属性
		mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
		return mapper;
	}

	protected void furtherInitMapper() {
	};

	/**
	 * Object可以是POJO，也可以是Collection或数组。 如果对象为Null, 返回"null". 如果集合为空集合, 返回"[]".
	 */
	public String toJson(Object object, SerializationFeature... features) {
		try {
			if (ArrayUtils.isNotEmpty(features)) {
				return mapper.writer(features[0], features).writeValueAsString(object);
			}
			return mapper.writeValueAsString(object);
		} catch (IOException e) {
			logger.warn("write to json string error:" + object, e);
			return null;
		}
	}

	/**
	 * 反序列化POJO或简单Collection如List<String>.
	 * 
	 * 如果JSON字符串为Null或"null"字符串, 返回Null. 如果JSON字符串为"[]", 返回空集合.
	 * 
	 * 如需反序列化复杂Collection如List<MyBean>, 请使用fromJson(String,JavaType)
	 * 
	 * @see #fromJson(String, JavaType)
	 */
	public <T> T fromJson(String jsonString, Class<T> clazz, DeserializationFeature... features) {
		if (StringUtils.isBlank(jsonString)) {
			return null;
		}

		try {
			if (ArrayUtils.isNotEmpty(features)) {
				return mapper.reader(features[0], features).withType(clazz).readValue(jsonString);
			}
			return mapper.readValue(jsonString, clazz);
		} catch (IOException e) {
			logger.warn("parse json string error:" + jsonString, e);
			return null;
		}
	}

	/**
	 * 反序列化复杂Collection如List<Bean>, 先使用函數createCollectionType构造类型,然后调用本函数.
	 * 
	 * @see #createCollectionType(Class, Class...)
	 */
	@SuppressWarnings("unchecked")
	public <T> T fromJson(String jsonString, JavaType javaType) {
		if (StringUtils.isBlank(jsonString)) {
			return null;
		}

		try {
			return (T) mapper.readValue(jsonString, javaType);
		} catch (IOException e) {
			logger.warn("parse json string error:" + jsonString, e);
			return null;
		}
	}

	public TypeFactory getTypeFactory() {
		return mapper.getTypeFactory();
	}

	/**
	 * 構造泛型的Collection Type如: ArrayList<MyBean>,
	 * 则调用constructCollectionType(ArrayList.class,MyBean.class)
	 * HashMap<String,MyBean>, 则调用(HashMap.class,String.class, MyBean.class)
	 */
	public JavaType createCollectionType(Class<?> collectionClass, Class<?>... elementClasses) {
		return mapper.getTypeFactory().constructParametricType(collectionClass, elementClasses);
	}

	/**
	 * 當JSON裡只含有Bean的部分屬性時，更新一個已存在Bean，只覆蓋該部分的屬性.
	 */
	@SuppressWarnings("unchecked")
	public <T> T update(String jsonString, T object) {
		try {
			return (T) mapper.readerForUpdating(object).readValue(jsonString);
		} catch (JsonProcessingException e) {
			logger.warn("update json string:" + jsonString + " to object:" + object + " error.", e);
		} catch (IOException e) {
			logger.warn("update json string:" + jsonString + " to object:" + object + " error.", e);
		}
		return null;
	}

	/**
	 * 輸出JSONP格式數據.
	 */
	public String toJsonP(String functionName, Object object) {
		return toJson(new JSONPObject(functionName, object));
	}
}
