package com.yupi.yuojcodesandbox;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.resource.ResourceUtil;
import cn.hutool.core.util.ArrayUtil;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.*;
import com.github.dockerjava.api.model.*;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.command.ExecStartResultCallback;
import com.yupi.yuojcodesandbox.model.ExecuteCodeRequest;
import com.yupi.yuojcodesandbox.model.ExecuteCodeResponse;
import com.yupi.yuojcodesandbox.model.ExecuteMessage;
import com.yupi.yuojcodesandbox.model.JudgeInfo;
import com.yupi.yuojcodesandbox.utils.ExecuteMsg2Response;
import com.yupi.yuojcodesandbox.utils.ProcessUtils;
import org.springframework.util.StopWatch;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class JavaDockerCodeSandboxOld implements CodeSandbox {

    private static final String GLOBAL_CODE_DIR_NANE = "tmpCode";

    private static final String GLOBAL_JAVA_CLASS_NAME = "Main.java";

    private static final String COMPILE_CODE_CMD_PRO = "javac -encoding utf-8 ";

    private static final long TIME_OUT = 5000L;

//    private static  Boolean first_init = true;
    private static  Boolean first_init = false;

    private ProcessUtils processUtils;

    //测试程序
    public static void main(String[] args) {
        JavaDockerCodeSandboxOld javaNativeCodeSandbox = new JavaDockerCodeSandboxOld();
        //构造请求体
        ExecuteCodeRequest executeCodeRequest = new ExecuteCodeRequest();
        executeCodeRequest.setInputList(Arrays.asList("1 2" , "1 3"));
        //读取一个代码文件
        //hutool的一个工具类，可以以Str读取resouce下的文件
        String code = ResourceUtil.readStr("testCode/simpleComputeArgs/SimpleCompute.java", StandardCharsets.UTF_8);
        executeCodeRequest.setCode(code);
        executeCodeRequest.setLanguage("java");

        ExecuteCodeResponse executeCodeResponse = javaNativeCodeSandbox.executeCode(executeCodeRequest);
        System.out.println(executeCodeResponse);

    }

    @Override
    public ExecuteCodeResponse executeCode(ExecuteCodeRequest executeCodeRequest) {
        //请求资源，文件目录等信息
        List<String> inputList = executeCodeRequest.getInputList();
        String code = executeCodeRequest.getCode();
        String language = executeCodeRequest.getLanguage();
        String userDir = System.getProperty("user.dir");//获得项目根目录
        String globalCodePathName = userDir + File.separator + GLOBAL_CODE_DIR_NANE;
        String userCodeParentPath = globalCodePathName + File.separator + UUID.randomUUID();
        String userCodePath = userCodeParentPath + File.separator + GLOBAL_JAVA_CLASS_NAME;
        //收到用户传来的代码，执行代码
        //用程序，代替人工，来操作命令行
        //1 . 把代码保存为文件
        File userCodeFile = SavaCodeFile(code , userCodePath);
        //2 、编译代码
        try {
            Process compileProcess = Runtime.getRuntime().exec(COMPILE_CODE_CMD_PRO + userCodeFile.getAbsolutePath());
            ExecuteMessage executeMessage = processUtils.runProcessAndGetMessage(compileProcess , "编译");
            System.out.println(executeMessage);
        } catch (Exception e) {
            return getErrorResponse(e);
        }

        //3.拉取一个java容器，然后把用户编译的代码放进去
        //获取默认的dockerClient
        DockerClient dockerClient = DockerClientBuilder.getInstance().build();

        String image = "openjdk:8-alpine";
        //3.1. 拉取镜像
        if(first_init) {
            PullImageCmd pullImageCmd = dockerClient.pullImageCmd(image);
            //拉取之后的回调函数，因为拉取可能要很长时间
            PullImageResultCallback pullImageResultCallback = new PullImageResultCallback(){
                @Override
                public void onNext(PullResponseItem item) {
                    System.out.println("下载镜像的状态：" + item.getStatus());
                    super.onNext(item);
                }
            };
            try {
                pullImageCmd
                        .exec(pullImageResultCallback)
                        .awaitCompletion();//await的作用是阻塞，直到下载完成才会执行下一步操作
            } catch (InterruptedException e) {
                System.out.println("下载异常");
                throw new RuntimeException(e);
            }
            System.out.println("下载完成");
            first_init = false;
        }

        //3.2 创建容器(创建的时候就把编译文件复制进去(容器挂载目录))
        CreateContainerCmd containerCmd = dockerClient.createContainerCmd(image);//创建容器的命令
        //创建容器时指定HostConfig ， 重要！
        HostConfig hostConfig = new HostConfig();//容器配置
        //bind就是和远程部署一样，Volume映射，把本地文件映射到容器中，让容器可以访问主机,把code父目录映射到/app
        //因此容器去访问/app下的main，就可以访问到文件了
        hostConfig.setBinds(new Bind(userCodeParentPath , new Volume("/app")));
        hostConfig.withMemory(1000*1000*1000L);//容器的内存，100M
        hostConfig.withMemorySwap(0L);//判题机当然禁止把内存里的值写到硬盘里来作弊
        hostConfig.withCpuCount(1L);

        //4. 操作容器，执行编译文件
        //4.1 创建容器
        CreateContainerResponse createContainerResponse = containerCmd
                .withHostConfig(hostConfig)
                .withNetworkDisabled(true)//禁止网络
                .withReadonlyRootfs(true) //禁止向根目录写入
                .withAttachStdin(true)//获取容器的输入输出
                .withAttachStdout(true)
                .withAttachStderr(true)
                .withTty(true)//交互终端
                .exec();
        String containerId = createContainerResponse.getId();//拿容器ID等会有用
        //启动容器
        dockerClient.startContainerCmd(containerId).exec();


        //4.2 执行命令，并且返回结果
        List<ExecuteMessage> executeMessageList = new ArrayList<>();//返回结果列表，每个用例都有执行信息
        //docker提供了exec keen_blackwell (java，，) 这种方式去操作容器的CMD，相当于在容器中执行命令
        //那么现在就是要用java命令执行那个编译文件 docker exec keen_blackwell java -cp /app Main 1 3
        for(String inputArgs : inputList) {
            ExecuteMessage executeMessage = new ExecuteMessage();
            StopWatch stopWatch = new StopWatch();//监听程序运行的时间
            //对于每一对用例，先把参数拿到
            String[] inputArgArray = inputArgs.split(" ");
            //首先需要一个命令数组cmdArr,就是等会准备执行这个命令，对于这一个用例
            String[] cmdArray = ArrayUtil.append(new String[]{"java" , "-cp" , "/app","Main"} , inputArgArray);
            //然后execCreateCmd创建一个在containerId容器中执行的命令cmdArray
            ExecCreateCmdResponse execCreateCmdResponse = dockerClient.execCreateCmd(containerId)
                    .withCmd(cmdArray)
                    .withAttachStdin(true)
                    .withAttachStderr(true)
                    .withAttachStdout(true)
                    .exec();
            System.out.println("创建cmd:" + execCreateCmdResponse);
            String execId = execCreateCmdResponse.getId();
            final String[] message = {null};//执行信息ExcuteMessage的初始化
            final String[] errorMessage = {null};
            long time = 0L;
            final boolean[] time_out = {true};//当前样例是否超时
            //创建好这个用例的命令后，就要执行他,但execStartCmd(execId)执行命令是异步的，所以要写一个回调
            ExecStartResultCallback execStartResultCallback = new ExecStartResultCallback() {
                //正常执行完会执行这个方法，如果执行到这里，说明没超时，如果被掐断了，说明超时了
                @Override
                public void onComplete() {
                    time_out[0] = false;
                    super.onComplete();
                }

                @Override
                public void onNext(Frame frame) {
                    StreamType streamType = frame.getStreamType();
                    if(StreamType.STDERR.equals(streamType)){
                        errorMessage[0] = new String(frame.getPayload());
                        System.out.println("错误的ERROR:" + errorMessage[0]);
                    }else{
                        message[0] = new String(frame.getPayload());
                        System.out.println("执行结果:\n" + message[0]);
                    }
                    super.onNext(frame);
                }
            };


            //4.3 再执行命令之前，我们要开一个统计进程，方便统计内存
            // 获取占用的内存
            final long[] maxMemory = {0L};
            StatsCmd statsCmd = dockerClient.statsCmd(containerId);
            ResultCallback<Statistics> statisticsResultCallback = statsCmd.exec(new ResultCallback<Statistics>() {

                @Override
                public void onNext(Statistics statistics) {
//                    System.out.println("内存占用：" + statistics.getMemoryStats().getUsage());
                    maxMemory[0] = Math.max(statistics.getMemoryStats().getUsage(), maxMemory[0]);
                }

                @Override
                public void close() throws IOException {

                }

                @Override
                public void onStart(Closeable closeable) {

                }

                @Override
                public void onError(Throwable throwable) {

                }

                @Override
                public void onComplete() {

                }
            });
            //在运行程序之前先开一个统计程序
            statsCmd.exec(statisticsResultCallback);

            //4.4 真正的执行
            try {
                stopWatch.start();
                dockerClient.execStartCmd(execId)    //真正开始执行
                        .exec(execStartResultCallback)
                        .awaitCompletion(TIME_OUT , TimeUnit.MILLISECONDS);//超时控制
                stopWatch.stop();
                time = stopWatch.getLastTaskTimeMillis();
                statsCmd.close();//程序结束了也要关闭统计进程
            } catch (InterruptedException e) {
                System.out.println("程序执行异常");
                throw new RuntimeException(e);
            }
            executeMessage.setMessage(message[0]);
            executeMessage.setErrorMessage(errorMessage[0]);
            executeMessage.setTime(time);
            executeMessage.setMemory(maxMemory[0]);
            executeMessageList.add(executeMessage);
        }

        //5. 整理结果
        ExecuteCodeResponse executeCodeResponse = ExecuteMsg2Response.executeMsg2Response(executeMessageList);
        //返回结果
        return executeCodeResponse;
    }

    /**
     * 处理错误的，提升健壮性,如果出问题了，我都先返回这个类，至少能正常的往前端传一些简单的错误信息，而不是程序崩溃
     * @param e
     * @return
     */
    private ExecuteCodeResponse getErrorResponse(Throwable e){
        ExecuteCodeResponse executeCodeResponse = new ExecuteCodeResponse();
        executeCodeResponse.setOutputList(new ArrayList<>());
        executeCodeResponse.setMessage(e.getMessage());
        executeCodeResponse.setStatus(2);//表示代码沙箱错误,我的程序出现了错误
        executeCodeResponse.setJudgeInfo(new JudgeInfo());
        return executeCodeResponse;
    }

    /**
     * 将用户代码保存成文件
     * @param code
     * @return
     */
    public static File SavaCodeFile(String code , String savePath){

        String userDir = System.getProperty("user.dir");//获得项目根目录
        String globalCodePathName = userDir + File.separator + GLOBAL_CODE_DIR_NANE;
        //先判断存代码的文件夹是否存在
        if(!FileUtil.exist(globalCodePathName)) {
            FileUtil.mkdir(globalCodePathName);//没有则新建
        }
        //把用户的代码隔离存放
        //用户的单独文件夹
        File userCodeFile = FileUtil.writeString(code, savePath, StandardCharsets.UTF_8);

        return userCodeFile;
    }

}
