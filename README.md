# Pistachio 

Pistachio is various annotation processing tools that are Java 17 compiled and modularized.



* META-INF/services generator similar [Kohsuke's metainf-services](https://github.com/kohsuke/metainf-services) and various others
* Fork of the [hickory annotation processer](https://javadoc.io/static/com.jolira/hickory/1.0.0/net/java/dev/hickory/prism/package-summary.html). 


The annotations are separated from their processors on purpose. This is to allow the processors to remain on the classpath 
and the annotations to be on the module-path which will happen if you have a `module-info` maven projects. 
Consequently you do not need use `<annotationProcessorPaths>` with this library.


The project is called Pistachio for two reasons:

* To honor the kick ass **Hickory** project that was still useful all these years (pistachio is a nut bearing tree as well...).
* JStachio is the parent organization and uses the project

## ServiceLoader helper

### How to use

Add the following to your pom dependencies.


```xml
    <dependency>
      <groupId>io.jstach.pistachio</groupId>
      <artifactId>pistachio-svc</artifactId>
      <version>1.1</version>
      <optional>true</optional>
      <scope>provided</scope>
    </dependency>
    <dependency>
      <groupId>io.jstach.pistachio</groupId>
      <artifactId>pistachio-svc-apt</artifactId>
      <version>1.1</version>
      <optional>true</optional>
      <scope>provided</scope>
    </dependency>
```

In module-info do:

```
requires static io.jstach.svc;
```

**DO NOT requires io.jstach.svc.apt   !!!**

See `@ServiceProvider` annotation javadoc.

## Prism generator 

A lot of folks are not aware of this but you should almost never use the actual annotation classes in an annotation processor, even your own.

Why is it problematic? An annotation processor is not guaranteed to have classes loaded, 
if your annotation references other classes (e.g. has a Class<?> parameter) that are not yours you will have issues, 
*especially* if you are dealing with modularized projects.

This is where Hickory is really helpful. However Hickory has not been updated since 2007!

## How to use

```xml
    <dependency>
      <groupId>io.jstach.pistachio</groupId>
      <artifactId>pistachio-prism</artifactId>
      <version>${pistachio.version}</version>
      <optional>true</optional>
      <scope>provided</scope>
    </dependency>
    <dependency>
      <groupId>io.jstach.pistachio</groupId>
      <artifactId>pistachio-prism-apt</artifactId>
      <version>${pistachio.version}</version>
      <optional>true</optional>
      <scope>provided</scope>
    </dependency>
```

In module-info do:

```
requires static io.jstach.prism;
```

**DO NOT requires io.jstach.prism.apt   !!!**

See javadoc for `@GeneratePrism`
