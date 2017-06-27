/*
 * <summary></summary>
 * <author>He Han</author>
 * <email>hankcs.cn@gmail.com</email>
 * <create-date>2014/12/23 20:07</create-date>
 *
 * <copyright file="CoreDictionaryACDAT.java" company="上海林原信息科技有限公司">
 * Copyright (c) 2003-2014, 上海林原信息科技有限公司. All Right Reserved, http://www.linrunsoft.com/
 * This source is subject to the LinrunSpace License. Please contact 上海林原信息科技有限公司 to get more information.
 * </copyright>
 */
package com.hankcs.hanlp.dictionary;

import com.hankcs.hanlp.HanLP;
import com.hankcs.hanlp.collection.trie.DoubleArrayTrie;
import com.hankcs.hanlp.corpus.io.ByteArray;
import com.hankcs.hanlp.corpus.io.IOUtil;
import com.hankcs.hanlp.corpus.tag.PosTag;
import com.hankcs.hanlp.util.LexiconUtility;
import com.hankcs.hanlp.util.Predefine;
import com.hankcs.hanlp.util.TextUtility;

import java.io.*;
import java.nio.file.Paths;
import java.util.*;

import static com.hankcs.hanlp.util.Predefine.logger;

/**
 * 使用DoubleArrayTrie实现的核心词典
 * @author hankcs
 */
public class CoreDictionary
{
    public static DoubleArrayTrie<PosTagInfo> trie = new DoubleArrayTrie<PosTagInfo>();
    public final static String path = HanLP.Config.CoreDictionaryPath;
    public static final int totalFrequency = 221894;

    // 自动加载词典
    static
    {
        long start = System.currentTimeMillis();
        if (load(path))
        {
            logger.info(path + "加载成功，" + trie.size() + "个词条，耗时" + (System.currentTimeMillis() - start) + "ms");
        }
        else
        {
            logger.severe("核心词典" + path + "加载失败");
            System.exit(-1);
        }
    }

    // 一些特殊的WORD_ID
    public static final int NR_WORD_ID = getWordID(Predefine.TAG_PEOPLE);
    public static final int NS_WORD_ID = getWordID(Predefine.TAG_PLACE);
    public static final int NT_WORD_ID = getWordID(Predefine.TAG_GROUP);
    public static final int T_WORD_ID = getWordID(Predefine.TAG_TIME);
    public static final int X_WORD_ID = getWordID(Predefine.TAG_CLUSTER);
    public static final int M_WORD_ID = getWordID(Predefine.TAG_NUMBER);
    public static final int NX_WORD_ID = getWordID(Predefine.TAG_PROPER);

    private static boolean load(String path)
    {
        logger.info("核心词典开始加载: " + path);
        if (loadDat(path)) return true;
        TreeMap<String, PosTagInfo> map = new TreeMap<String, PosTagInfo>();
        BufferedReader br = null;
        try
        {
            br = new BufferedReader(new InputStreamReader(IOUtil.newInputStream(path), "UTF-8"));
            String line;
            int MAX_FREQUENCY = 0;
            long start = System.currentTimeMillis();
            while ((line = br.readLine()) != null)
            {
//                System.out.println(line);
                String param[] = line.split("\\s+");
                int natureCount = (param.length - 1) / 2;
                PosTagInfo attribute = new PosTagInfo(natureCount);
                for (int i = 0; i < natureCount; ++i)
                {
                    attribute.pos[i] = Enum.valueOf(PosTag.class, param[1 + 2 * i]);
                    attribute.frequency[i] = Integer.parseInt(param[2 + 2 * i]);
                    attribute.totalFrequency += attribute.frequency[i];
                }
                map.put(param[0], attribute);
                MAX_FREQUENCY += attribute.totalFrequency;
            }
            logger.info("核心词典读入词条" + map.size() + " 全部频次" + MAX_FREQUENCY + "，耗时" + (System.currentTimeMillis() - start) + "ms");
            br.close();
            trie.build(map);
            logger.info("核心词典加载成功:" + trie.size() + "个词条，下面将写入缓存……");
            try
            {
                DataOutputStream out = new DataOutputStream(IOUtil.newOutputStream(path + Predefine.BIN_EXT));
                Collection<PosTagInfo> attributeList = map.values();
                out.writeInt(attributeList.size());
                for (PosTagInfo attribute : attributeList)
                {
                    out.writeInt(attribute.totalFrequency);
                    out.writeInt(attribute.pos.length);
                    for (int i = 0; i < attribute.pos.length; ++i)
                    {
                        out.writeInt(attribute.pos[i].ordinal());
                        out.writeInt(attribute.frequency[i]);
                    }
                }
                trie.save(out);
                out.close();
            }
            catch (Exception e)
            {
                logger.warning("保存失败" + e);
                return false;
            }
        }
        catch (FileNotFoundException e)
        {
            logger.warning("核心词典" + path + "不存在！" + e);
            return false;
        }
        catch (IOException e)
        {
            logger.warning("核心词典" + path + "读取错误！" + e);
            return false;
        }

        return true;
    }

    /**
     * 从磁盘加载双数组
     *
     * @param path
     * @return
     */
    static boolean loadDat(String path)
    {
        if (!new File(path + Predefine.BIN_EXT).exists())
            return false;
        try
        {
            ByteArray byteArray = ByteArray.createByteArray(path + Predefine.BIN_EXT);
            if (byteArray == null) return false;
            int size = byteArray.nextInt();
            PosTagInfo[] attributes = new PosTagInfo[size];
            final PosTag[] natureIndexArray = PosTag.values();
            for (int i = 0; i < size; ++i)
            {
                // 第一个是全部频次，第二个是词性个数
                int currentTotalFrequency = byteArray.nextInt();
                int length = byteArray.nextInt();
                attributes[i] = new PosTagInfo(length);
                attributes[i].totalFrequency = currentTotalFrequency;
                for (int j = 0; j < length; ++j)
                {
                    attributes[i].pos[j] = natureIndexArray[byteArray.nextInt()];
                    attributes[i].frequency[j] = byteArray.nextInt();
                }
            }
            if (!trie.load(byteArray, attributes) || byteArray.hasMore()) return false;
        }
        catch (Exception e)
        {
            logger.warning("读取失败，问题发生在" + e);
            return false;
        }
        return true;
    }

    /**
     * 获取条目
     * @param key
     * @return
     */
    public static PosTagInfo get(String key)
    {
        return trie.get(key);
    }

    /**
     * 获取条目
     * @param wordID
     * @return
     */
    public static PosTagInfo get(int wordID)
    {
        return trie.get(wordID);
    }

    /**
     * 获取词频
     *
     * @param term
     * @return
     */
    public static int getTermFrequency(String term)
    {
        PosTagInfo attribute = get(term);
        if (attribute == null) return 0;
        return attribute.totalFrequency;
    }

    /**
     * 是否包含词语
     * @param key
     * @return
     */
    public static boolean contains(String key)
    {
        return trie.get(key) != null;
    }

    /**
     * 核心词典中的词属性
     */
    static public class PosTagInfo implements Serializable
    {
        /**
         * 词性列表
         */
        public PosTag pos[];
        /**
         * 词性对应的词频
         */
        public int frequency[];

        public int totalFrequency;

        // 几个预定义的变量

//        public static PosTagInfo NUMBER = new PosTagInfo()

        public PosTagInfo(int size)
        {
            pos = new PosTag[size];
            frequency = new int[size];
        }

        public PosTagInfo(PosTag[] nature, int[] frequency)
        {
            this.pos = nature;
            this.frequency = frequency;
        }

        public PosTagInfo(PosTag nature, int frequency)
        {
            this(1);
            this.pos[0] = nature;
            this.frequency[0] = frequency;
            totalFrequency = frequency;
        }

        public PosTagInfo(PosTag[] nature, int[] frequency, int totalFrequency)
        {
            this.pos = nature;
            this.frequency = frequency;
            this.totalFrequency = totalFrequency;
        }

        /**
         * 使用单个词性，默认词频1000构造
         *
         * @param nature
         */
        public PosTagInfo(PosTag nature)
        {
            this(nature, 1000);
        }

        public static PosTagInfo create(String natureWithFrequency)
        {
            try
            {
                String param[] = natureWithFrequency.split(" ");
                int natureCount = param.length / 2;
                PosTagInfo attribute = new PosTagInfo(natureCount);
                for (int i = 0; i < natureCount; ++i)
                {
                    attribute.pos[i] = LexiconUtility.convertStringToNature(param[2 * i], null);
                    attribute.frequency[i] = Integer.parseInt(param[1 + 2 * i]);
                    attribute.totalFrequency += attribute.frequency[i];
                }
                return attribute;
            }
            catch (Exception e)
            {
                logger.warning("使用字符串" + natureWithFrequency + "创建词条属性失败！" + TextUtility.exceptionToString(e));
                return null;
            }
        }

        /**
         * 从字节流中加载
         * @param byteArray
         * @param natureIndexArray
         * @return
         */
        public static PosTagInfo create(ByteArray byteArray, PosTag[] natureIndexArray)
        {
            int currentTotalFrequency = byteArray.nextInt();
            int length = byteArray.nextInt();
            PosTagInfo attribute = new PosTagInfo(length);
            attribute.totalFrequency = currentTotalFrequency;
            for (int j = 0; j < length; ++j)
            {
                attribute.pos[j] = natureIndexArray[byteArray.nextInt()];
                attribute.frequency[j] = byteArray.nextInt();
            }

            return attribute;
        }

        /**
         * 获取词性的词频
         *
         * @param nature 字符串词性
         * @return 词频
         * @deprecated 推荐使用Nature参数！
         */
        public int getNatureFrequency(String nature)
        {
            try
            {
                PosTag pos = Enum.valueOf(PosTag.class, nature);
                return getNatureFrequency(pos);
            }
            catch (IllegalArgumentException e)
            {
                return 0;
            }
        }

        /**
         * 获取词性的词频
         *
         * @param nature 词性
         * @return 词频
         */
        public int getNatureFrequency(final PosTag nature)
        {
            int i = 0;
            for (PosTag pos : this.pos)
            {
                if (nature == pos)
                {
                    return frequency[i];
                }
                ++i;
            }
            return 0;
        }

        /**
         * 是否有某个词性
         * @param nature
         * @return
         */
        public boolean hasNature(PosTag nature)
        {
            return getNatureFrequency(nature) > 0;
        }

        /**
         * 是否有以某个前缀开头的词性
         * @param prefix 词性前缀，比如u会查询是否有ude, uzhe等等
         * @return
         */
        public boolean hasNatureStartsWith(String prefix)
        {
            for (PosTag n : pos)
            {
                if (n.startsWith(prefix)) return true;
            }
            return false;
        }

        @Override
        public String toString()
        {
            final StringBuilder sb = new StringBuilder();
            for (int i = 0; i < pos.length; ++i)
            {
                sb.append(pos[i]).append(' ').append(frequency[i]).append(' ');
            }
            return sb.toString();
        }

        public void save(DataOutputStream out) throws IOException
        {
            out.writeInt(totalFrequency);
            out.writeInt(pos.length);
            for (int i = 0; i < pos.length; ++i)
            {
                out.writeInt(pos[i].ordinal());
                out.writeInt(frequency[i]);
            }
        }
    }

    /**
     * 获取词语的ID
     * @param a 词语
     * @return ID,如果不存在,则返回-1
     */
    public static int getWordID(String a)
    {
        return CoreDictionary.trie.exactMatchSearch(a);
    }
}
