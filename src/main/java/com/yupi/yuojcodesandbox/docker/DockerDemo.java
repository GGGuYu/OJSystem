package com.yupi.yuojcodesandbox.docker;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.*;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.api.model.Image;
import com.github.dockerjava.api.model.PullResponseItem;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.command.LogContainerResultCallback;

import java.util.List;

public class DockerDemo {
    public static void main(String[] args) throws InterruptedException {
        //获取默认的dockerClient
        DockerClient dockerClient = DockerClientBuilder.getInstance().build();

//        PingCmd pingCmd = dockerClient.pingCmd();
//        pingCmd.exec();
        String image = "nginx:latest";
//        //1. 拉取镜像
//        PullImageCmd pullImageCmd = dockerClient.pullImageCmd(image);
//        //拉取之后的回调函数，因为拉取可能要很长时间
//        PullImageResultCallback pullImageResultCallback = new PullImageResultCallback(){
//            @Override
//            public void onNext(PullResponseItem item) {
//                System.out.println("下载镜像的状态：" + item.getStatus());
//                super.onNext(item);
//            }
//        };
//        //执行拉取镜像的操作
//        pullImageCmd
//                .exec(pullImageResultCallback)
//                .awaitCompletion();//await的作用是阻塞，直到下载完成才会执行下一步操作
//        System.out.println("下载完成");

        //2. 创建容器
        CreateContainerCmd containerCmd = dockerClient.createContainerCmd(image);//创建容器的命令
        //exec执行创建命令，然后withCMD是启动时自动执行这个命令
        CreateContainerResponse createContainerResponse = containerCmd
                .withCmd("echo", "Hello Docker")
                .exec();
        System.out.println(createContainerResponse);
        String containerId = createContainerResponse.getId();//拿容器ID等会有用

        //3. 查看容器状态
        List<Container> containerList = dockerClient.listContainersCmd().withShowAll(true).exec();
        for(Container container : containerList) {
            System.out.println(container);
        }

        //4. 启动容器
        dockerClient.startContainerCmd(containerId).exec();

//        Thread.sleep(5000L);//只是一个测试，因为怀疑启动是异步的，导致容器还没启动，就已经输出日志开始，结果我们拿不到日志

        //5.查看日志，也有一个回调，因为日志可能很大，所以执行查看日志之后，阻塞等待输出完，然后执行回调函数
        LogContainerResultCallback logContainerResultCallback = new LogContainerResultCallback() {
            @Override
            public void onNext(Frame item) {
                System.out.println(item.getStreamType());
                System.out.println("日志：" + new String(item.getPayload()));
                super.onNext(item);
            }
        };
        // 阻塞等待日志输出
        dockerClient.logContainerCmd(containerId)
                .withStdErr(true)
                .withStdOut(true)
                .exec(logContainerResultCallback)
                .awaitCompletion();

        //6. 强制删除容器
        //可以学到链式调用，如果我要写一个dockerClient的话，也可以考虑用.with的方法来拼接命令
        dockerClient.removeContainerCmd(containerId).withForce(true).exec();

        //7. 删除镜像
        dockerClient.removeImageCmd(image).exec();

    }
}
