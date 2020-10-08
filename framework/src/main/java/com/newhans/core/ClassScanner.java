package com.newhans.core;

import java.io.IOException;
import java.net.JarURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class ClassScanner {
    public static List<Class<?>> scanClasses(String packageName) throws IOException, ClassNotFoundException {
        //T:一类 ？:不知道多少不同的
        List<Class<?>> classList = new ArrayList<>();
        String path = packageName.replace(".","/");
        //获取默认的类加载器-->应该是Application Class Loader
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        //双亲委派机制:Application Class Loader -> Extension Class Loader -> Bootstrap Class Loader
        Enumeration<URL> resources = classLoader.getResources(path);
        while (resources.hasMoreElements()){
            URL resource = resources.nextElement();
            if (resource.getProtocol().contains("jar")){
                JarURLConnection jarURLConnection = (JarURLConnection) resource.openConnection();
                String jarFilePath = jarURLConnection.getJarFile().getName();
                classList.addAll(getClassesFromJar(jarFilePath, path));
            }else{
                //TODO
            }
        }
        return classList;
    }

    private static List<Class<?>> getClassesFromJar(String jarFilePath, String path) throws IOException, ClassNotFoundException {
        //1.传入jar包的路径
        //2.一个jar包中有很多类文件，我们需要指定哪些类文件是我们需要的，可以通过类的相对路径来指定
        List<Class<?>> classes = new ArrayList<>();
        JarFile jarFile = new JarFile(jarFilePath);
        Enumeration<JarEntry> jarEntries = jarFile.entries();
        while (jarEntries.hasMoreElements()){
            JarEntry jarEntry = jarEntries.nextElement();
            String entryName = jarEntry.getName();
            //jar entry : eg. com/newhans/Test.class
            if (entryName.startsWith(path) && entryName.endsWith((".class"))){
                String classFullName = entryName.replace("/", ".").substring(0, entryName.length() - 6);
                //反射：获得类对象
                classes.add(Class.forName(classFullName));
            }
        }
        return classes;
    }
}
