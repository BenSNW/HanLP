/*
 * <summary></summary>
 * <author>He Han</author>
 * <email>hankcs.cn@gmail.com</email>
 * <create-date>2014/12/9 20:57</create-date>
 *
 * <copyright file="FeatureFunction.java" company="上海林原信息科技有限公司">
 * Copyright (c) 2003-2014, 上海林原信息科技有限公司. All Right Reserved, http://www.linrunsoft.com/
 * This source is subject to the LinrunSpace License. Please contact 上海林原信息科技有限公司 to get more information.
 * </copyright>
 */
package com.hankcs.hanlp.model.crf;

import com.hankcs.hanlp.corpus.io.ByteArray;
import com.hankcs.hanlp.corpus.io.ICacheAble;

import java.io.DataOutputStream;

/**
 * 每个特征函数对应tag.size个输出，其实是特征函数的集合
 *
 * 每一行%x[#,#]生成一个CRFs中的点(state)函数: f(s, feature), 其中s为t时刻的的标签(output)，o为t时刻的上下文.
 * 如CRF++说明文件中的示例函数: func1 = if (output = B and feature="U02:那") return 1 else return 0
 *
 * 按照[id] [参数o]的格式排列，你可能会奇怪，f(s, feature)应该接受两个参数才对。其实s隐藏起来了，注意到id不是连续的，
 * 而是隔了四个，这表示这四个标签（s=b|m|e|s）和公共的参数o组合成了四个特征函数。
 *
 * 特别的，0-15为BEMS转移到BEMS的转移函数，也就是f(s', s, feature=null)。
 *
 * @author hankcs
 */
public class FeatureFunction implements ICacheAble
{
    /**
     * 环境参数
     */
    char[] feature;
    /**
     * 标签参数
     */
//    String s;

    /**
     * 每个tag输出的权值，按照index对应于tag的id
     */
    double[] weight;

    public FeatureFunction(char[] o, int tagSize)
    {
        this.feature = o;
        weight = new double[tagSize];
    }

    public FeatureFunction()
    {
    }

    @Override
    public void save(DataOutputStream out) throws Exception
    {
        out.writeInt(feature.length);
        for (char c : feature)
        {
            out.writeChar(c);
        }
        out.writeInt(weight.length);
        for (double v : weight)
        {
            out.writeDouble(v);
        }
    }

    @Override
    public boolean load(ByteArray byteArray)
    {
        int size = byteArray.nextInt();
        feature = new char[size];
        for (int i = 0; i < size; ++i)
        {
            feature[i] = byteArray.nextChar();
        }
        size = byteArray.nextInt();
        weight = new double[size];
        for (int i = 0; i < size; ++i)
        {
            weight[i] = byteArray.nextDouble();
        }
        return true;
    }
}
