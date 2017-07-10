/*
 * <summary></summary>
 * <author>He Han</author>
 * <email>hankcs.cn@gmail.com</email>
 * <create-date>2014/11/19 14:16</create-date>
 *
 * <copyright file="CoreDictionaryTransformMatrixDictionary.java" company="上海林原信息科技有限公司">
 * Copyright (c) 2003-2014, 上海林原信息科技有限公司. All Right Reserved, http://www.linrunsoft.com/
 * This source is subject to the LinrunSpace License. Please contact 上海林原信息科技有限公司 to get more information.
 * </copyright>
 */
package com.hankcs.hanlp.dictionary;

import com.hankcs.hanlp.HanLP;
import com.hankcs.hanlp.corpus.tag.PosTag;

import static com.hankcs.hanlp.util.Predefine.logger;

/**
 * 核心词典词性转移矩阵
 * @author hankcs
 */
public class CoreDictionaryTransformMatrixDictionary
{
    public static TransformMatrixDictionary<PosTag> posTagTrDictionary;
    static
    {
        posTagTrDictionary = new TransformMatrixDictionary<>(PosTag.class);
        long start = System.currentTimeMillis();
        if (!posTagTrDictionary.load(HanLP.Config.CoreDictionaryTagTrPath))
        {
            logger.severe("加载核心词典词性转移矩阵" + HanLP.Config.CoreDictionaryTagTrPath + "失败");
            System.exit(-1);
        }
        else
        {
            logger.info("加载核心词典词性转移矩阵" + HanLP.Config.CoreDictionaryTagTrPath + "成功，耗时：" + (System.currentTimeMillis() - start) + " ms");
        }
    }
}
