package com.yupi.yuojcodesandbox;
import cn.hutool.core.io.resource.ResourceUtil;
import com.yupi.yuojcodesandbox.model.ExecuteCodeRequest;
import com.yupi.yuojcodesandbox.model.ExecuteCodeResponse;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;


@Component
public class JavaNativeCodeSandbox extends JavaCodeSandboxTemplate {


    // 新增测试用main方法 , 这个类只有一个测试方法
    public static void main(String[] args) {
        CodeSandbox sandbox = new JavaNativeCodeSandbox();

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
