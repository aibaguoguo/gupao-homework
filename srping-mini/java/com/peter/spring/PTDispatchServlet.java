package com.peter.spring;

import com.peter.annotation.*;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class PTDispatchServlet extends HttpServlet {

    //配置文件封装--application.properties
    private Properties applicationConfig = new Properties();
    //储存扫描到的类信息
    private List<String> classNames = new ArrayList<>();
    //ioc容器
    private Map<String, Object> iocMap = new ConcurrentHashMap<>();
    //对url映射的封装
    private List<HandlerMapping> handlerMappings = new ArrayList<>();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        doDispatch(req, resp);
    }

    /**
     * 根据请求,匹配url
     * 通过反射调用方法
     *
     * @param req
     * @param resp
     */
    private void doDispatch(HttpServletRequest req, HttpServletResponse resp) {
        String uri = req.getRequestURI();
        for (HandlerMapping handlerMapping : handlerMappings) {
            if (handlerMapping.hasUrl(uri)) {
                //反射调用
                Object returnObj = null;
                Object[] params = getParams(handlerMapping,req,resp);
                try {
                    returnObj = handlerMapping.getMethod().invoke(handlerMapping.getInstance(), params);
                    if (returnObj==null||returnObj instanceof Void) {
                        return;
                    } else {
                        resp.getWriter().write(returnObj.toString());
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

    }

    /**
     * 获取参数列表
     * @param handlerMapping
     * @param req
     * @param resp
     * @return
     */
    private Object[] getParams(HandlerMapping handlerMapping, HttpServletRequest req, HttpServletResponse resp) {
        Object[] params = new Object[handlerMapping.getParamTypes().length];
        for(int i=0;i<handlerMapping.getParamTypes().length;i++){
            if(handlerMapping.getParamTypes()[i] == HttpServletRequest.class){
                params[i]=req;
                continue;
            }

            if(handlerMapping.getParamTypes()[i] == HttpServletResponse.class){
                params[i]=resp;
                continue;
            }

            Annotation[][] annotations = handlerMapping.getMethod().getParameterAnnotations();
            for (Annotation annotation : annotations[i]) {
                if(annotation instanceof PTRequestParam){
                    //获取请求参数列表
                    Map<String, String[]> parameterMap = req.getParameterMap();
                    String[] param = parameterMap.get(((PTRequestParam) annotation).value());
                    //进行类型转换
                    params[i] = convert(Arrays.toString(param).replaceAll("\\[|\\]","")
                            .replaceAll("\\s",","),handlerMapping.getParamTypes()[i]);
                }
            }

        }

        return params;
    }

    private Object convert(Object value,Class<?> clazz){
        if(clazz == int.class){
            return Integer.valueOf((String)value);
        }else if(clazz == String.class){
            return value.toString();
        }else
        return null;
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        doGet(req, resp);
    }

    @Override
    public void init(ServletConfig config) throws ServletException {
        try {
            //读取配置信息
            doLoadConfig(config);
            //扫描配置的目标路径
            doScanner(applicationConfig.getProperty("scanPackage"));
            //初始化
            doInitInstance();
            //依赖注入
            doAutowired();
            //url映射
            initHandlerMapping();
        } catch (Exception e) {
            e.printStackTrace();
        }

        System.out.println("PTString is Started...");
    }

    /**
     * 初始化url映射信息
     */
    private void initHandlerMapping() {
        //遍历ioc容器
        for (Map.Entry<String, Object> entry : iocMap.entrySet()) {
            Object instance = entry.getValue();
            Class type = instance.getClass();
            //只处理被加了@PTContorller的类
            if (!type.isAnnotationPresent(PTContorller.class)) continue;
            //遍历方法
            for (Method method : type.getMethods()) {
                if (method.isAnnotationPresent(PTRequestMapping.class)) {
                    String url = method.getAnnotation(PTRequestMapping.class).value();
                    HandlerMapping h = new HandlerMapping(method, url, instance);
                    h.setParamTypes(method.getParameterTypes());
                    handlerMappings.add(h);
                    System.out.println("init mapping url:"+url+",method:"+type.getName()+"#"+method.getName());
                }
            }

        }

    }

    /**
     * 对url映射的封装
     */
    class HandlerMapping {
        private Method method;
        private String url;
        private Object instance;
        private Class<?>[] paramTypes;//型参列表

        public HandlerMapping(Method method, String url, Object instance) {
            this.method = method;
            this.url = url;
            this.instance = instance;
        }

        public boolean hasUrl(String url) {
            return this.url.equals(url);
        }

        public Method getMethod() {
            return method;
        }

        public void setMethod(Method method) {
            this.method = method;
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public Object getInstance() {
            return instance;
        }

        public void setInstance(Object instance) {
            this.instance = instance;
        }

        public Class<?>[] getParamTypes() {
            return paramTypes;
        }

        public void setParamTypes(Class<?>[] paramTypes) {
            this.paramTypes = paramTypes;
        }
    }

    /**
     * 依赖注入
     */
    private void doAutowired() throws IllegalAccessException {
        for (Map.Entry<String, Object> entry : iocMap.entrySet()) {
            Object instance = entry.getValue();//实例
            Class clazz = instance.getClass();//类型
            if (clazz.isAnnotationPresent(PTService.class)
                    || clazz.isAnnotationPresent(PTContorller.class)) {
                //获取所有的全局变量
                Field[] fields = clazz.getDeclaredFields();
                for (Field field : fields) {
                    if (!field.isAnnotationPresent(PTAutowired.class)) continue;
                    PTAutowired autowired = field.getAnnotation(PTAutowired.class);
                    String beanName = autowired.value();
                    if ("".equals(beanName)) {
                        beanName = toLowerFirstCase(field.getType().getSimpleName());
                    }
                    if (iocMap.containsKey(beanName)) {
                        field.setAccessible(true);
                        field.set(instance, iocMap.get(beanName));
                    }
                }

            }
        }

    }

    /**
     * 初始化扫描到的类信息,将其放入ioc容器中
     */
    private void doInitInstance() throws Exception {
        for (String className : classNames) {

            Class<?> clazz = Class.forName(className);
            if (clazz.isAnnotationPresent(PTContorller.class)) {
                //默认首字母小写
                iocMap.put(toLowerFirstCase(clazz.getSimpleName()), clazz.newInstance());
            } else if (clazz.isAnnotationPresent(PTService.class)) {
                /**
                 * 分好几种情况
                 * 1、指定了别名，用别名
                 * 2、没有指定别名，默认类名首字母小写
                 * 3、接口有多个实现类，抛出异常,如果只有一个默认用实现类实例
                 */
                PTService s = clazz.getAnnotation(PTService.class);//需要Class<?>声明 不然得强转
                String beanName = s.value();
                if ("".equals(beanName)) {
                    beanName = toLowerFirstCase(clazz.getSimpleName());
                }
                Object instance = clazz.newInstance();
                iocMap.put(beanName, instance);

                //接口注入
                for (Class<?> anInterface : clazz.getInterfaces()) {
                    //如果一个接口只有一个实现类 储存为  key-value:接口名-实现类实例
                    //如果一个接口有多个实例 储存为 key-vlaue:*接口名-null
                    if (iocMap.containsKey(anInterface.getSimpleName())) {
                        throw new RuntimeException("The Class [" + anInterface.getName() + "] has multiple implementations!");
                    } else {
                        iocMap.put(toLowerFirstCase(anInterface.getSimpleName()), instance);
                    }
                }

            }

        }
    }

    private String toLowerFirstCase(String className) {
        char[] chars = className.toCharArray();
        chars[0] += 32;
        return String.valueOf(chars);
    }

    /**
     * 扫描目标路径
     *
     * @param scanPackage
     */
    private void doScanner(String scanPackage) {

        //读取出来的路径是com.**.** 需要转换成com/**/**
        String filePath = scanPackage.replaceAll("\\.", "/");

        //获得文件绝对路径
        URL url = this.getClass().getClassLoader().getResource(filePath);
        File classPath = new File(url.getFile());

        //循环遍历
        for (File file : classPath.listFiles()) {
            if (file.isDirectory()) {
                doScanner(scanPackage + "." + file.getName());
            } else {
                if (!file.getName().endsWith("class")) continue;
                classNames.add(scanPackage + "." + file.getName().replace(".class", ""));
            }
        }
    }

    /**
     * 加载配置文件
     *
     * @param config
     */
    private void doLoadConfig(ServletConfig config) {
        InputStream is = null;
        try {
            //读取application.properties文件
            is = this.getClass().getClassLoader().getResourceAsStream(config.getInitParameter("config"));
            applicationConfig.load(is);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                is.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }


    public static void main(String[] args) {

    }

}
