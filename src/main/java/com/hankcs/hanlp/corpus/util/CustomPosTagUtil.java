/*
 * <summary></summary>
 * <author>He Han</author>
 * <email>me@hankcs.com</email>
 * <create-date>2016/1/4 16:02</create-date>
 *
 * <copyright file="CustomNatureUtility.java" company="码农场">
 * Copyright (c) 2008-2016, 码农场. All Right Reserved, http://www.hankcs.com/
 * This source is subject to Hankcs. Please contact Hankcs to get more information.
 * </copyright>
 */
package com.hankcs.hanlp.corpus.util;

import com.hankcs.hanlp.corpus.tag.PosTag;
import com.hankcs.hanlp.dictionary.CoreDictionaryTransformMatrixDictionary;
import com.hankcs.hanlp.dictionary.CustomDictionary;
import com.hankcs.hanlp.recognition.nr.PersonRecognition;
import com.hankcs.hanlp.recognition.nt.OrganizationRecognition;
import com.hankcs.hanlp.seg.common.Vertex;

import java.util.Map;
import java.util.TreeMap;

import static com.hankcs.hanlp.util.Predefine.logger;

/**
 * 运行时动态增加词性工具
 *
 * @author hankcs
 */
public class CustomPosTagUtil
{
    static
    {
        logger.warning("已激活自定义词性功能,由于采用了反射技术,用户需对本地环境的兼容性和稳定性负责!\n" +
               "如果用户代码X.java中有switch(pos)语句,需要调用CustomNatureUtility.registerSwitchClass(X.class)注册X这个类");
    }
    private static Map<String, PosTag> extraValueMap = new TreeMap<>();

    /**
     * 动态增加词性工具
     */
    private static EnumBuster<PosTag> enumBuster = new EnumBuster<>(
            PosTag.class, CustomDictionary.class, Vertex.class, PersonRecognition.class, OrganizationRecognition.class);

    /**
     * 增加词性
     * @param name 词性名称
     * @return 词性
     */
    public static PosTag addPosTag(String name)
    {
        PosTag customNature = extraValueMap.get(name);
        if (customNature != null) return customNature;
        customNature = enumBuster.make(name);
        enumBuster.addByValue(customNature);
        extraValueMap.put(name, customNature);
        // 必须对词性标注HMM模型中的元组做出调整
        CoreDictionaryTransformMatrixDictionary.posTagTrDictionary.extendSize();

        return customNature;
    }

    /**
     * 注册switch(pos)语句类
     * @param switchUsers 任何使用了switch(pos)语句的类
     */
    public static void registerSwitchClass(Class... switchUsers)
    {
        enumBuster.registerSwitchClass(switchUsers);
    }

    /**
     * 还原对词性的全部修改
     */
    public static void restore()
    {
        enumBuster.restore();
        extraValueMap.clear();
    }
}
