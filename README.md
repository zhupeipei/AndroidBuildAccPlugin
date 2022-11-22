# Android编译速度优化——模块Aar方案实现

- [Android编译速度优化——模块Aar方案实现](#android编译速度优化——模块aar方案实现)
    - [插件如何使用](#插件如何使用)
        - [引入插件](#引入插件)
    - [插件的实现方案](#插件的实现方案)
        - [插件实现的大致流程](#插件实现的大致流程)
        - [插件和编译生命周期的交互&插件apply的时机](#插件和编译生命周期的交互插件apply的时机)
        - [如何确定需要加速编译的模块](#如何确定需要加速编译的模块)
        - [如何将模块发布为aar](#如何将模块发布为aar)
        - [如何替换本地模块依赖为aar依赖](#如何替换本地模块依赖为aar依赖)
        - [模块中libs文件处理](#模块中libs文件处理)
        - [模块修改时如何发现，并更改为源码依赖](#模块修改时如何发现，并更改为源码依赖)
        - [加速编译的模块退化为源码依赖](#加速编译的模块退化为源码依赖)
    - [插件效果](#插件效果)
    - [参考文章](#参考文章)


现在android项目大部分采用模块化的开发方案，在项目clean/build时需要对所有模块进行重新编译，而对于大多数开发人员来说，基本上只关系自己负责的少数几个模块，而不需要修改其他模块的代码，这些模块的编译时间就可以作为被优化的一个点。目前模块的aar方案在头部大厂已经成为标配方案，因此本文将围绕aar方案进行阐述其中的一些核心要点。

插件代码存放在[https://github.com/zhupeipei/AndroidBuildAccPlugin](https://github.com/zhupeipei/AndroidBuildAccPlugin)，供各位使用。

## 插件如何使用

### 引入插件
![](https://s2.loli.net/2022/11/22/tJlAO6eSKviGBX8.jpg)

![](https://s2.loli.net/2022/11/22/RhO61nBNurdjIEM.jpg)

其中插件的配置类如下图，插件提供了如下功能：
1. 配置（移除）一些需要添加（删除）的模块；
2. 模块打包aar文件发布方式，如远程依赖的方式供所有业务方使用，还是打包为本地依赖的方式，更快捷

![](https://s2.loli.net/2022/11/22/AO7cNYhtlC6RzJX.jpg)

## 插件的实现方案
### 插件实现的大致流程
模块aar化大致流程为模块打包为aar，模块本身的lib依赖打包为aar或者jar包上传到maven库，在打包过程中替换本地的模块依赖为远程依赖。这两块任务是有依赖关系的，在没有上传到maven库前不能进行替换，否则会出现资源找不到的情况。在开发的过程中还遇到了其他一些问题，如生命周期的使用不当会导致替换无法生效、模块间有的aar依赖有的源码依赖导致类冲突的问题等。下面将详细介绍下插件中的一些细节点，也算作我在开发插件中间的一些回顾。

### 插件和编译生命周期的交互&插件apply的时机

Gradle生命周期一般分为三个阶段
1. 初始化阶段，解析setting.gradle文件，确定需要引入哪些模块；
2. 配置阶段，解析每个project的build.gradle文件，build.gradle就是一堆代码的集合，只是用dsl的语言风格描述出来，看着像配置语言。同时汇总所有project生成有向无环图来确定每个task的依赖关系；
3. 执行阶段，根据上个阶段生成的task依赖关系图，依次执行所有task。

![20210504143410675](https://s2.loli.net/2022/11/22/oLJsBUA6MrQ18jm.png)

在开发插件中，Gradle在构建的各个阶段都提供了回调，如下图所示。
![1](https://s2.loli.net/2022/11/22/9blK5dBaPWVN8me.png)


结合这两张图可以看出插件的apply必须在配置阶段，同时模块aar发布需要maven-publish插件的apply，因此必须在每个模块的afterEvaluate中执行（如果不是，会报错），这样插件的apply最好是在rootProject中依赖，这样通过对所有project插入afterEvaluate的监听即可。代码如下：
![](https://s2.loli.net/2022/11/22/iQC2wAGpsHERdI8.jpg)

需要注意的是，一般在所有project的build.gradle执行完成后会回调projectsEvaluated，然后开始分析task依赖并回调taskGraph.whenReady方法。但是gradle有一项优化
```
org.gradle.configureondemand=true
```
如果开启的话，会执行配置优化，会导致taskGraph.whenReady提前执行，导致生命周期混乱，因此需要将该配置关闭。

### 如何确定需要加速编译的模块
在默认状态下，除了App和RootProject模块，项目中的其他模块都会被纳入到加速编译的模块。当然也可以通过BuildAccExtension来配置需要加入加速编译的模块（includeBundles）和不纳入加速编译的模块（excludeBundles）。
includeBundles只是作为考虑的模块纳入加速编译中，如果当前模块依赖的子模块在excludeBundles中，那么当前模块也不能进行加速编译，也就是说excludeBundles的优先级比includeBundles高。另外如果当前模块对应的aar还没有生成，这时模块需要等待一次打包生成对应的aar后才会真正被使用。
### 如何将模块发布为aar
和在gradle脚本中发布aar一样，也是通过maven-publish插件实现的。打包的内容通过buildType来区分，maven配置groupId为com.buildAcc拼接上模块名称、artifactId为模块名称加上buildType、version为1.0.0。这样在assembleDebug或者assembleRelease会根据各自的产物打包为不同的aar。这里有个细节点，也可以说maven-publish插件的一个bug吧，就是如果我们对每个buildType都定义一个publish的配置，这时在A模块依赖B模块打包aar时，会有4个publish配置，我们简单定义为publishADebug、publishARelease、publishBDebug、publishBRelease，这时执行publishADebug时，因为存在依赖关系，需要publishBDebug、publishBRelease这两配置，maven-publish插件就懵逼了，不知道怎么选择。这个解决会比较困难，具体可以参考[https://stackoverflow.com/questions/51247830/publishing-is-not-able-to-resolve-a-dependency-on-a-project-with-multiple-public](https://stackoverflow.com/questions/51247830/publishing-is-not-able-to-resolve-a-dependency-on-a-project-with-multiple-public)。因此，在打包时最好执行assembleDebug或者assembleRelease，如果非要执行assemble任务怎么办呢，这时只能选择一种buildType，可以通过BuildAccExtension的buildType来配置需要publish哪种buildType。

![](https://s2.loli.net/2022/11/22/xWscytqui7HPRoE.jpg)

![](https://s2.loli.net/2022/11/22/1PKvJiYdmWhlM9k.jpg)

![](https://s2.loli.net/2022/11/22/cMe1hL3F2Tim6YW.jpg)

在配置完maven-publish任务后，因为正常的打包流程不会打包为aar，因此还需要先执行打包为aar，其次执行publish任务，本文实现的依赖关系为bundleAarTask依赖app模块的assembleTask，publishTask依赖模块的bundleAarTask。

![](https://s2.loli.net/2022/11/22/KvNRru1wkn57dhU.jpg)


### 如何替换本地模块依赖为aar依赖
替换本地模块依赖为aar依赖，需要在所有项目projectsEvaluated之后，taskGraph.whenReady之前执行。替换逻辑就是遍历每个项目configurationList的依赖，如果发现是项目依赖，并且项目对应的mavenRepo里存在，这时候就会替换为aar依赖。

![](https://s2.loli.net/2022/11/22/BDnUNtzxq2iu1ow.jpg)


### 模块中libs文件处理
如果模块中有一些jar、aar的文件依赖，形式如下```api(name: 'moduleNameXXX', ext: 'aar')```，这类jar、aar依赖也需要上传到maven库中，否则会出现依赖找不到的情况，libs文件发布和aar发布流程类似，同时因为libs文件发布不需要附加信息，可以执行的非常靠前。本项目是在打包流程的第一个任务执行的，具体可以参考github源码，这里不再赘述。

### 模块修改时如何发现，并更改为源码依赖

模块在修改后，之前依赖的aar文件已经没法使用了，这时需要依赖新的aar文件。那么应该如何进行工程感知呢。本文没有引入一些其他的开源方案，而是简单的通过获取文件的最后修改时间来判断子模块是否进行了修改。为过滤一些无效的文件夹和文件，可以通过BuildAccExtension中whiteListFolder、whiteListFile来配置需要过滤的文件。

### 加速编译的模块退化为源码依赖
前面说明了通过BuildAccExtension，以及简单的模块依赖关系过滤一些不需要加速编译的模块，在项目都配置完成后，还需要再次全局遍历下，以解决子模块虽然被纳入加速编译模块中但是mavenRepo不存在的问题。例如，libA依赖host模块，host模块虽然参与了加速编译，但其对应的aar还未生成，不能纳入加速编译同时libA也需要被过滤掉；另外libA依赖host模块，app模块依赖libA模块，host模块aar未生成，这时libA需要被过滤掉，同时app的libA依赖不能被替换，只能继续通过源码依赖的方式。如果在项目中使用出现了Duplicate class的问题，应该直接通过./gradlew dependencies来查看依赖是否冲突。

![](https://s2.loli.net/2022/11/22/z9Tvk26i3qf8NZJ.jpg)


## 插件效果
最后，在实际项目中体验了下，在没有代码修改的情况下，正常打包在9m左右；而通过该插件自动依赖远程aar包，打包时间平均为2m30s，整体编译时间优化了近70%，效果还是非常显著的，理论上模块越多节省的编译时间会更加显著。看到这里，屏幕前的你还不赶快来尝试下么，保熟！


## 参考文章
1. [gradle lifecycle](https://docs.gradle.org/current/userguide/build_lifecycle.html)
2. [https://www.bilibili.com/video/BV1DE411Z7nt](https://www.bilibili.com/video/BV1DE411Z7nt)
3. [gradle生命周期](https://juejin.cn/post/6844903741762568206)
4. [gradle task学习](https://www.jianshu.com/p/c45861426eba)
5. [gradle依赖管理](http://gradledoc.githang.com/1.12/userguide/dependency_management.html)
6. [gradle框架之二：依赖实现分析](https://mp.weixin.qq.com/s/WQCqUaYDPHDIjUHlxjRIkw)
7. [Android 编译速度优化黑科技 - RocketX](https://juejin.cn/post/7038157787976695815)
