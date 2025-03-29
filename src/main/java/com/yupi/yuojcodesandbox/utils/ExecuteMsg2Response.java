package com.yupi.yuojcodesandbox.utils;

import cn.hutool.core.util.StrUtil;
import com.yupi.yuojcodesandbox.model.ExecuteCodeResponse;
import com.yupi.yuojcodesandbox.model.ExecuteMessage;
import com.yupi.yuojcodesandbox.model.JudgeInfo;

import java.util.ArrayList;
import java.util.List;

public class ExecuteMsg2Response {
    public static ExecuteCodeResponse executeMsg2Response(List<ExecuteMessage> executeMessageList){
        //5. 整理结果
        ExecuteCodeResponse executeCodeResponse = new ExecuteCodeResponse();//响应
        List<String> outputList = new ArrayList<>();//刚刚执行的答案
        long maxTime = 0L;//取耗时最大值
        long maxMemory = 0L;//取内存占用最大值
        for(ExecuteMessage executeMessage : executeMessageList){
            String errorMessage = executeMessage.getErrorMessage();
            //某个示例直接执行错误了，失败了
            if(StrUtil.isNotBlank(errorMessage)){
                executeCodeResponse.setMessage(errorMessage);
                //执行中存在错误,是用户的代码编译了，但是执行有错误，报错了
                executeCodeResponse.setStatus(3);
                break;
            }
            //正常能运行的示例，但是不一定正确
            outputList.add(executeMessage.getMessage());
            Long time = executeMessage.getTime();
            Long memory = executeMessage.getMemory();
            if(time != null){
                maxTime = Math.max(maxTime, time);
            }
            if(memory != null){
                maxMemory = Math.max(maxMemory, memory);
            }
        }
        if(outputList.size() == executeMessageList.size()){
            //如果所有的示例都是正常的运行了，那么本次沙箱没有出现运行之外的错误
            executeCodeResponse.setStatus(1);//表示执行成功没有报错
            executeCodeResponse.setMessage("正常");//本次代码沙箱执行的情况，和判题无关
        }
        //只要没有运行中发生错误，那不管怎么样都应该把运行的结果输出出去
        executeCodeResponse.setOutputList(outputList);
        JudgeInfo judgeInfo = new JudgeInfo();

        judgeInfo.setTime(maxTime);//代码沙箱执行花费的时间
        //这个非常麻烦，晚点操作
        judgeInfo.setMemory(maxMemory);//代码沙箱执行占用的内存
        executeCodeResponse.setJudgeInfo(judgeInfo);

        return executeCodeResponse;
    }
}
