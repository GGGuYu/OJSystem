package com.yupi.yuojcodesandbox;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.resource.ResourceUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.dfa.FoundWord;
import cn.hutool.dfa.WordTree;
import com.yupi.yuojcodesandbox.model.ExecuteCodeRequest;
import com.yupi.yuojcodesandbox.model.ExecuteCodeResponse;
import com.yupi.yuojcodesandbox.model.ExecuteMessage;
import com.yupi.yuojcodesandbox.model.JudgeInfo;
import com.yupi.yuojcodesandbox.security.DefaultSecurityManager;
import com.yupi.yuojcodesandbox.utils.ProcessUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class JavaNativeCodeSandbox implements CodeSandbox {

    private static final String GLOBAL_CODE_DIR_NANE = "tmpCode";

    private static final String GLOBAL_JAVA_CLASS_NAME = "Main.java";

    private static final String COMPILE_CODE_CMD_PRO = "javac -encoding utf-8 ";

    //限制了资源大小为256MB
    private static final String RUN_JAVA_CMD_PRO = "java -Xmx256m -Dfile.encoding=UTF-8 -cp ";

    private static final String RUN_JAVA_CLASS = " Main ";

    //示例黑名单，如果代码出现这些黑名单有危险的词方法或者对象名，我就不给他编译直接
    private static final List<String> BlackList = Arrays.asList("Files" , "exec");

    //字典树，用来创建更高效的字符匹配的数据结构
    private static final WordTree WORD_TREE;

    //静态方法块，也就是这个静态对象(也就是大类第一次加载程序的时候，执行的代码)
    static {
        WORD_TREE = new WordTree();
        WORD_TREE.addWords(BlackList);//黑名单载入字典树
    }

    private ProcessUtils processUtils;

    //测试程序
    public static void main(String[] args) {
        JavaNativeCodeSandbox javaNativeCodeSandbox = new JavaNativeCodeSandbox();
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
//        System.setSecurityManager(new DefaultSecurityManager()); 已经弃用了好像

        //请求资源，文件目录等信息
        List<String> inputList = executeCodeRequest.getInputList();
        String code = executeCodeRequest.getCode();
        String language = executeCodeRequest.getLanguage();
        String userDir = System.getProperty("user.dir");//获得项目根目录
        String globalCodePathName = userDir + File.separator + GLOBAL_CODE_DIR_NANE;
        String userCodeParentPath = globalCodePathName + File.separator + UUID.randomUUID();
        String userCodePath = userCodeParentPath + File.separator + GLOBAL_JAVA_CLASS_NAME;
        //先校验用户传来的代码有没有黑名单中的词汇，做安全处理
        FoundWord foundWord = WORD_TREE.matchWord(code);//代码匹配字典树中的词汇，看看找到哪些
        if(foundWord != null) {
            //如果确实有高危词汇
            System.out.println("包含高危词汇：" + foundWord.getFoundWord());
            return null;
        }
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
        //3. 执行代码得到运行结果
        List<ExecuteMessage> executeMessageList = new ArrayList<>();
        for(String inputArgs : inputList)
        {
            String runCmd = RUN_JAVA_CMD_PRO + userCodeParentPath + RUN_JAVA_CLASS + inputArgs;
            try {
                Process runProcess = Runtime.getRuntime().exec(runCmd);
                ExecuteMessage executeMessage = ProcessUtils.runProcessAndGetMessage(runProcess, "运行用户代码文件");
                System.out.println(executeMessage);
                executeMessageList.add(executeMessage);
            } catch (Exception e) {
                return getErrorResponse(e);
            }
        }
        //4. 整理结果
        ExecuteCodeResponse executeCodeResponse = new ExecuteCodeResponse();//响应
        List<String> outputList = new ArrayList<>();//刚刚执行的答案
        long maxTime = 0;//取耗时最大值
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
            if(time != null){
                maxTime = Math.max(maxTime, time);
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
//        judgeInfo.setMemory();//代码沙箱执行占用的内存
        executeCodeResponse.setJudgeInfo(judgeInfo);
        
        //5. 做清理，不然不回收资源机器爆炸了
        if(userCodeFile.getParentFile() != null)
        {
            //确保代码文件的父目录不为空，再删.直接删整个父目录
            boolean del = FileUtil.del(userCodeParentPath);
            System.out.println("删除" + (del ? "成功" : "失败"));
        }

        //6. 处理错误，提升健壮性，简单写一下,封装一个方法，抛异常的时候直接返回

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
