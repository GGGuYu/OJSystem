package com.yupi.yuojcodesandbox.utils;

import com.yupi.yuojcodesandbox.model.ExecuteMessage;
import org.springframework.util.StopWatch;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;

public class ProcessUtils {


    /**
     * 执行Process并且获得执行信息
     * @param runProcess
     * @return ExecuteMessage
     */
    public static ExecuteMessage runProcessAndGetMessage(Process runProcess ,String opName) {
        ExecuteMessage executeMessage = new ExecuteMessage();

        try {
            StopWatch stopWatch = new StopWatch();
            stopWatch.start();
            //等待程序执行 获取错误码
            int exitValue = 0;//进程结束将获得一个退出码
            exitValue = runProcess.waitFor();
            executeMessage.setExitValue(exitValue);
            //正常推出
            if(exitValue == 0) {
                System.out.println(opName + "执行成功");
                String consoleMessage = GetConsoleMessage(runProcess , exitValue);
                executeMessage.setMessage(consoleMessage);
            }else {
                //异常退出
                System.out.println(opName + "执行失败 , 错误码：" + exitValue);
                String consoleErrorMessage = GetConsoleMessage(runProcess, exitValue);
                executeMessage.setErrorMessage(consoleErrorMessage);
            }
            stopWatch.stop();
            long lastTaskTimeMillis = stopWatch.getLastTaskTimeMillis();
            executeMessage.setTime(lastTaskTimeMillis);
        } catch (Exception e) {
            e.printStackTrace();
        }

        return executeMessage;
    }



    /**
     * 获取控制台输出
     * @param process
     * @param exitValue
     * @return String
     */
    public static String GetConsoleMessage(Process process , Integer exitValue) {
        BufferedReader bufferedReader;
        StringBuilder outputStringBuilder = new StringBuilder();
        if(exitValue == 0){
            //正常退出那输入流
            bufferedReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        }else{
            //异常退出拿错误流
            bufferedReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
        }

        String MessageLine;
        //逐行读取
        while(true){
            try {
                if ((MessageLine = bufferedReader.readLine()) == null) break;
                outputStringBuilder.append(MessageLine);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        return outputStringBuilder.toString();
    }

}
