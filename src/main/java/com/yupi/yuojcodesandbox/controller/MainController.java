package com.yupi.yuojcodesandbox.controller;


import com.yupi.yuojcodesandbox.CodeSandbox;
import com.yupi.yuojcodesandbox.JavaDockerCodeSandbox;
import com.yupi.yuojcodesandbox.model.ExecuteCodeRequest;
import com.yupi.yuojcodesandbox.model.ExecuteCodeResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

@RestController("/")
public class MainController {

    @Resource
    @Qualifier("javaDockerCodeSandboxOld")
    private CodeSandbox codeSandbox;
//    @Resource
//    private JavaDockerCodeSandbox codeSandbox;

    @GetMapping("health")
    public String healthCheck() {
        return "ok";
    }

    /**
     * 执行代码API
     * @param executeCodeRequest
     * @return
     */
    @PostMapping("/executeCode")
    ExecuteCodeResponse executeCode(@RequestBody ExecuteCodeRequest executeCodeRequest){
        if(executeCodeRequest == null){
            throw new RuntimeException("请求信息为空");
        }
        return codeSandbox.executeCode(executeCodeRequest);
    }
}
