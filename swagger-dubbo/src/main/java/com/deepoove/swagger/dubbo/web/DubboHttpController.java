package com.deepoove.swagger.dubbo.web;

import com.deepoove.swagger.dubbo.config.SwaggerDubboProperties;
import com.deepoove.swagger.dubbo.http.HttpMatch;
import com.deepoove.swagger.dubbo.http.IRefrenceManager;
import com.deepoove.swagger.dubbo.reader.NameDiscover;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.swagger.annotations.Api;
import io.swagger.util.Json;
import io.swagger.util.PrimitiveType;
import org.apache.commons.lang3.StringUtils;
import org.apache.dubbo.common.utils.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.HashSet;
import java.util.Map.Entry;
import java.util.Set;

@Controller
@RequestMapping("${swagger.dubbo.http:h}")
@Api(hidden = true)
public class DubboHttpController {
    
    @Autowired
    IRefrenceManager refrenceManager;

	private static Logger logger = LoggerFactory.getLogger(DubboHttpController.class);

	@Autowired
	SwaggerDubboProperties swaggerDubboConfig;

	@RequestMapping(value = "/{interfaceClass}/{methodName}",
			produces = "application/json; charset=utf-8")
	@ResponseBody
	public ResponseEntity<String> invokeDubboA(@PathVariable("interfaceClass") String interfaceClass,
			@PathVariable("methodName") String methodName, HttpServletRequest request,
			HttpServletResponse response) throws Exception {


		return invokeDubbo(interfaceClass, methodName, null, request, response);
	}

	@RequestMapping(value = "/{interfaceClass}/{methodName}/{operationId}",
			produces = "application/json; charset=utf-8")
	@ResponseBody
	public ResponseEntity<String> invokeDubboByOpId(@PathVariable("interfaceClass") String interfaceClass,
													@PathVariable("methodName") String methodName,
													@PathVariable("operationId") String operationId, HttpServletRequest request,
													HttpServletResponse response) throws Exception {
		return invokeDubbo(interfaceClass, methodName, operationId, request, response);
	}

	private ResponseEntity<String> invokeDubbo(String interfaceClass, String methodName,
		   String operationId, HttpServletRequest request, HttpServletResponse response) throws Exception {

		if (!swaggerDubboConfig.isEnable()) { return new ResponseEntity<String>(HttpStatus.NOT_FOUND); }

		Object ref = null;
		Method method = null;
		Object result = null;
		BufferedReader reader = new BufferedReader(new InputStreamReader(request.getInputStream()));
		String bodyParams = IOUtils.read(reader);
		
		Entry<Class<?>, Object> entry = refrenceManager.getRef(interfaceClass);
		
		if (null == entry){
		    logger.info("No Ref Service FOUND.");
		    return new ResponseEntity<String>(HttpStatus.NOT_FOUND);
		}
		ref = entry.getValue();
		HttpMatch httpMatch = new HttpMatch(entry.getKey(), ref.getClass());
		Method[] interfaceMethods = httpMatch.findInterfaceMethods(methodName);

		Set<String> keySet = new HashSet<>(request.getParameterMap().keySet());
		if (StringUtils.isNotBlank(bodyParams)){
			JsonObject departmentJsonObj = new JsonParser().parse(bodyParams).getAsJsonObject();
			if (departmentJsonObj != null){
				keySet.addAll(departmentJsonObj.keySet());
			}
		}

		if (null != interfaceMethods && interfaceMethods.length > 0) {
			// 用方法上@Apioperation注解的nickname字段 匹配 OperationId 获取到正确的重载方法
			Method[] refMethods = httpMatch.findRefMethods(interfaceMethods, operationId,
					request.getMethod());
			method = httpMatch.matchRefMethod(refMethods, methodName, keySet);
		}
		if (null == method) {
		    logger.info("No Service Method FOUND.");
			return new ResponseEntity<String>(HttpStatus.NOT_FOUND);
		}
		// 获取方法的参数名
		String[] parameterNames = NameDiscover.parameterNameDiscover.getParameterNames(method);

		if (parameterNames == null && "java.lang.reflect.Proxy".equals(ref.getClass().getGenericSuperclass().getTypeName())){
			// 处理对象被代理的情况
			final Method realMethod = getRealMethodByObjName(ref.toString(), operationId, methodName, request.getMethod(), keySet);
			if (realMethod == null){ return new ResponseEntity<String>(HttpStatus.NOT_FOUND); }
			parameterNames = NameDiscover.parameterNameDiscover.getParameterNames(realMethod);
		}
		
		logger.info("[Swagger-dubbo] Invoke by " + swaggerDubboConfig.getCluster());
		if (SwaggerDubboProperties.CLUSTER_RPC.equals(swaggerDubboConfig.getCluster())){
    		ref = refrenceManager.getProxy(interfaceClass);
    		if (null == ref){
    		    logger.info("No Ref Proxy Service FOUND.");
                return new ResponseEntity<String>(HttpStatus.NOT_FOUND);
    		}
    		method = ref.getClass().getMethod(method.getName(), method.getParameterTypes());
    		if (null == method) {
    		    logger.info("No Proxy Service Method FOUND.");
                return new ResponseEntity<String>(HttpStatus.NOT_FOUND);
            }
		}
		logger.debug("[Swagger-dubbo] Invoke dubbo service method:{},req parameter:{}, body parameter:{}",
				method, Json.pretty(request.getParameterMap()), bodyParams);
		if (null == parameterNames || parameterNames.length == 0) {
			result = method.invoke(ref);
		} else {
			Object[] args = new Object[parameterNames.length];
			Type[] parameterTypes = method.getGenericParameterTypes();
			Class<?>[] parameterClazz = method.getParameterTypes();

			for (int i = 0; i < parameterNames.length; i++) {
				Object suggestPrameterValue = suggestPrameterValue(parameterTypes[i],
							parameterClazz[i], request.getParameter(parameterNames[i]), bodyParams);
				args[i] = suggestPrameterValue;
			}
			result = method.invoke(ref, args);
		}
		return ResponseEntity.ok(Json.mapper().writeValueAsString(result));
	}

    private Object suggestPrameterValue(Type type, Class<?> cls, String reqParameter, String bodyParams)
			throws JsonParseException, JsonMappingException, IOException {
		PrimitiveType fromType = PrimitiveType.fromType(type);
		if (null != fromType) {
			// form k-v参数 ——> java基本类型
			DefaultConversionService service = new DefaultConversionService();
			boolean actual = service.canConvert(String.class, cls);
			if (actual) { return service.convert(reqParameter, cls); }
		} else {
			// form k-v参数(json)、request body参数(json) ——> java pojo
			String parameter = reqParameter != null ? reqParameter : bodyParams;
			try {
				// form k-v 中的json参数处理
				return Json.mapper().readValue(parameter, cls);
			} catch (Exception e) {
				throw new IllegalArgumentException("The parameter value [" + parameter + "] should be json of [" + cls.getName() + "] Type.", e);
			}

		}
		try {
			return Class.forName(cls.getName()).newInstance();
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

//	/**
//	 * 创建 cls 对象，并从 parametersMap 中拿出键值赋予对象中相同K的列值
//	 */
//	private Object generateObj(Class<?> cls, Map<String, String> parametersMap)
//			throws JsonParseException, JsonMappingException, IOException {
//		try{
//			Class<?> forName = Class.forName(cls.getName());
//			Constructor<?> constructor = forName.getDeclaredConstructor();
//			constructor.setAccessible(true);
//			Object obj = constructor.newInstance();
//			for (String key : parametersMap.keySet()) {
//				try{
//					Field field = forName.getDeclaredField(key);
//					field.setAccessible(true);
//					field.set(obj, parametersMap.get(key));
//				} catch (Exception e) {
//					logger.info("this key: {} not in class: {}", key, cls.getName());
//				}
//			}
//			return obj;
//		}catch (Exception e){
//			logger.info("数据封装失败", e);
//		}
//		return null;
//	}


	/**
	 * 考虑对象被代理情况，获取真实对象的相应Method
	 * @param objName 实现类toString 如：com.abc.xxImpl@12b34bd5
	 * @param methodName 方法名
	 * @return 真实的Method 对象
	 */
	private Method getRealMethodByObjName(String objName, String operationId, String methodName, String requestMethod, Set<String> keySet){
		Method method = null;
		try {
			String name = objName.split("@")[0];
			Class<?> clazz = Class.forName(name);
			HttpMatch httpMatch = new HttpMatch(clazz, clazz);
			Method[] methods = httpMatch.findInterfaceMethods(methodName);
			Method[] refMethods = httpMatch.findRefMethods(methods, operationId, requestMethod);
			method = httpMatch.matchRefMethod(refMethods, methodName, keySet);
		}catch (Exception e){
			logger.info("get real method fail");
		}
		return method;
	}

}
