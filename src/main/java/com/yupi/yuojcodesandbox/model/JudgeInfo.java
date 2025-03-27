package com.yupi.yuojcodesandbox.model;

import lombok.Data;

/**
 * 判题信息
 */
@Data
public class JudgeInfo {

    /**
     * 判题状态
     */
    private String message;
    /**
     * 消耗时间
     */
    private long time;
    /**
     * 消耗内存
     */
    private long memory;

}
