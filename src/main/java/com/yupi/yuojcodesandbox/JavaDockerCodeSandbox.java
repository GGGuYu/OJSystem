package com.yupi.yuojcodesandbox;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.resource.ResourceUtil;
import cn.hutool.core.util.ArrayUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.dfa.FoundWord;
import cn.hutool.dfa.WordTree;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.*;
import com.github.dockerjava.api.model.*;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.command.ExecStartResultCallback;
import com.yupi.yuojcodesandbox.model.ExecuteCodeRequest;
import com.yupi.yuojcodesandbox.model.ExecuteCodeResponse;
import com.yupi.yuojcodesandbox.model.ExecuteMessage;
import org.springframework.stereotype.Component;
import org.springframework.util.StopWatch;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Component("javaDockerCodeSandbox")
public class JavaDockerCodeSandbox extends JavaCodeSandboxTemplate{

    private static final long TIME_OUT = 5000L;

    private static  Boolean first_init = false;


    //重写运行代码的逻辑，变成使用docker来运行代码
    @Override
    public List<ExecuteMessage> runCode(List<String> inputList , File userCodeFile) {
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
        hostConfig.setBinds(new Bind(userCodeFile.getParentFile().getAbsolutePath() , new Volume("/app")));
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
        //返回结果
        return executeMessageList;
    }
    // 新增测试用main方法
    public static void main(String[] args) {
        CodeSandbox sandbox = new JavaDockerCodeSandbox();

        // 构造测试请求
        ExecuteCodeRequest request = new ExecuteCodeRequest();
        request.setLanguage("java");
        request.setInputList(Arrays.asList("1 3", "1 4"));

        // 读取测试代码文件（需要准备测试文件）
        String code = ResourceUtil.readStr("testCode/simpleComputeArgs/SimpleCompute.java", StandardCharsets.UTF_8);
        request.setCode(code);

        try {
            ExecuteCodeResponse response = sandbox.executeCode(request);
            System.out.println("执行状态: " + response.getStatus());
            System.out.println("执行耗时: " + response.getJudgeInfo().getTime() + "ms");
            System.out.println("内存占用: " + response.getJudgeInfo().getMemory() + "B");
            System.out.println("输出结果: " + response.getOutputList());
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            // 清理临时文件（根据实际路径调整）
//            FileUtil.del(new File("tmpCode"));
        }
    }

}
