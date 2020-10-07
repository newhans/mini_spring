package com.newhans.beans;

import com.newhans.aop.*;
import com.newhans.web.mvc.Controller;
import org.omg.CORBA.ObjectHelper;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class BeanFactory {
    private static Map<Class<?>, Object> beans = new ConcurrentHashMap<>();

    /**
     *带有@AutoWired注解修饰的属性的类
     */
    private static Set<Class<?>> beansHasAutoWiredField = Collections.synchronizedSet(new HashSet<>());

    public static Object getBean(Class<?> cls){
        return beans.get(cls);
    }

    /**
     * 根据类列表classList来查找所有需要初始化的类放入Component工厂
     * 并且处理类中所有带@Autowired注解的属性的依赖问题
     * @param classList
     * @throws Exception
     */
    public static void initBean(List<Class<?>> classList) throws Exception {
        /*因为类定义后续处理类中@RequestMapping注解生成处理器时还要使用
        因此这里要创建新容器，不能修改原引用
         */
        List<Class<?>> classesToCreate = new ArrayList<>(classList);
        //被@aspect注解的切面类
        List<Class<?>> aspectClasses = new ArrayList<>();

        for (Class<?> aClass : classesToCreate){
            if (aClass.isAnnotationPresent(Aspect.class)){
                aspectClasses.add(aClass);
            }else{
                createBean(aClass);
            }
        }

        //使用动态代理来处理AOP
        resolveAOP(aspectClasses);

        //todo
        //有的类中某个属性已经通过@Autowired注入了旧的被代理的对象，重新创建他们
        for (Class<?> aClass : beansHasAutoWiredField){
            createBean(aClass);
        }
    }

    private static void createBean(Class<?> aClass) throws IllegalAccessException, InstantiationException {
        //只处理带@Component注解或者@Controller注解的类
        if (!aClass.isAnnotationPresent(Component.class) && !aClass.isAnnotationPresent(Controller.class)){
            return;
        }
        //初始化对象
        Object bean = aClass.newInstance();
        //遍历类中所有定义的字段，如果字段带有@Autowired注解，则需要注入对应依赖
        for (Field field : bean.getClass().getDeclaredFields()){
            if (!field.isAnnotationPresent(AutoWired.class)){
                continue;
            }
            //将需要注入其他Bean的类保存起来，因为要等到AOP代理类生成之后，需要更新他们
            BeanFactory.beansHasAutoWiredField.add(aClass);
            Class<?> fieldType = field.getType();
            field.setAccessible(true);
            /*
             *只能对可以访问的字段使用get和set方法（不能访问的比如：private字段），所以get和set（这里的get和set不是getter和setter，
             * 而是java.lang.reflect.Field下的get和set方法）。java安全机制允许查看一个对象有哪些字段，但除非拥有访问权限，否则
             * 不允许读写那些字段的值
             * 反射机制的默认行为受限于java的访问控制。不过，可以调用Field,Method和Constructor对象的setAccessible方法
             * 覆盖java的访问控制，这个方法是AccessibleObject类的一个方法，它是Field,Method,Constructor类的公共超类
             */
            //field.setAccessible(true);
            if (fieldType.isInterface()){
                //如果依赖的类型是接口，则查询其实现类
                //class1.isAssignableFrom(class2) = true 代表class2是class1类型，可分配class2对象给class1
                for (Class<?> key : BeanFactory.beans.keySet()){
                    if (fieldType.isAssignableFrom(key)){
                        fieldType = key;
                        break;
                    }
                }
            }
            field.set(bean, BeanFactory.getBean(fieldType));
        }
        //todo
        //这里可能Autowired注入失败，例如存在循环依赖，或者bean工厂中根本不存在，目前暂时先不处理
        beans.put(aClass, bean);
    }

    /**
     * 对于所有被@Aspect注解修饰的类
     * 遍历它们定义的方法，处理@Pointcut、@Before、@After注解
     */
    private static void resolveAOP(List<Class<?>> aspectClasses) throws IllegalAccessException, InstantiationException, ClassNotFoundException {
        if (aspectClasses.size() == 0) return;

        for (Class<?> aClass : aspectClasses){
            Method before = null;
            Method after = null;
            Object target = null;

            String method = null;
            String pointcutName = null;

            //初始化对象，为了简单起见，假设每一个代理类，最多只有一个切点，一个前置以及一个后置处理器
            //所以我们也必须先处理pointcut，再解析before和after方法
            Object bean = aClass.newInstance();
            for (Method m : bean.getClass().getDeclaredMethods()){
                if (m.isAnnotationPresent(Pointcut.class)){
                    //com.newhans.demo.rapper.rap()
                    String pointcut = m.getAnnotation(Pointcut.class).value();
                    String classStr = pointcut.substring(0, pointcut.lastIndexOf("."));
                    target = Thread.currentThread().getContextClassLoader().loadClass(classStr).newInstance();
                    method = pointcut.substring(pointcut.lastIndexOf(".") + 1);
                    pointcutName = m.getName();
                }
            }

            for (Method m : bean.getClass().getDeclaredMethods()){
                //todo
                if (m.isAnnotationPresent(Before.class)){
                    String value = m.getAnnotation(Before.class).value();
                    value = value.substring(0, value.indexOf("("));
                    if (value.equals(pointcutName)){
                        before = m;
                    }
                }else if (m.isAnnotationPresent(After.class)){
                    String value = m.getAnnotation(After.class).value();
                    value = value.substring(0, value.indexOf("("));
                    if (value.equals((pointcutName))){
                        after = m;
                    }
                }
            }

            //获取代理对象并更新bean工厂
            Object proxy = new ProxyDynamic().createProxy(bean, before, after, target, method.substring(0, method.indexOf("(")));

            BeanFactory.beans.put(target.getClass(), proxy);
        }
    }
}
