# OkHttpUtil
okhttp二次封装工具类（基于鸿洋大神的okhttputil库） 
[![](https://jitpack.io/v/LongAgoLong/OkHttpUtil.svg)](https://jitpack.io/#LongAgoLong/OkHttpUtil)  
**gradle依赖**
```java
allprojects {
	repositories {
		...
		maven { url 'https://jitpack.io' }
	}
}
```
```java
implementation 'com.github.LongAgoLong:OkHttpUtil:okhttplib:$JitPack-Version$'
implementation 'com.github.LongAgoLong:OkHttpUtil:okhttputiljar:$JitPack-Version$'
implementation 'com.squareup.okhttp3:okhttp:3.14.0'
implementation 'com.squareup.okio:okio:1.17.2'
```
**maven依赖**
```java
<repositories>
	<repository>
		<id>jitpack.io</id>
		<url>https://jitpack.io</url>
	</repository>
</repositories>
```
```java
<dependency>
	<groupId>com.github.LongAgoLong</groupId>
	<artifactId>OkHttpUtil</artifactId>
	<version>$JitPack-Version$</version>
</dependency>
```
