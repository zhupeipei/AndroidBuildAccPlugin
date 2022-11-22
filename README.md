# AndroidBuildAccPlugin
android 编译加速方案

遇到的问题
1. 如果在com.android.application中应用插件会有什么问题；
2. 如何解决项目依赖项目的问题
3. org.gradle.configureondemand=false 这里需要检查下
4. 添加只对assemble的启动参数才应用插件
5. api依赖替换，导致的依赖向上传递的问题（其实是因为如果lib1依赖lib2，lib2对应的jar/aar不存在，此时lib1也不能做替换）
6. 删除依赖的本地aar包，导致编译失败的问题
7. 依赖调整完成后，task没有减少 (因为assembleRelease的task有publish的task依赖)
8. 源码一起打包
9. clean任务执行的时候需要删除maven_local的任务

主app执行
9m50s、9m6s、8m51s

2m41s、2m4s、2m3s

如果项目不在加速编译的项目中，那么该项目以及父项目都不应该参与加速编译；
如果加速编译的项目对应的aar文件还没有生成，那么该项目、父项目、子项目都不应该参与编译
