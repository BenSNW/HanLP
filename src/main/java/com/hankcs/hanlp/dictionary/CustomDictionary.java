/*
 * <summary></summary>
 * <author>He Han</author>
 * <email>hankcs.cn@gmail.com</email>
 * <create-date>2014/5/10 12:42</create-date>
 *
 * <copyright file="WordDictionary.java" company="上海林原信息科技有限公司">
 * Copyright (c) 2003-2014, 上海林原信息科技有限公司. All Right Reserved, http://www.linrunsoft.com/
 * This source is subject to the LinrunSpace License. Please contact 上海林原信息科技有限公司 to get more information.
 * </copyright>
 */
package com.hankcs.hanlp.dictionary;


import com.hankcs.hanlp.HanLP;
import com.hankcs.hanlp.collection.AhoCorasick.AhoCorasickDoubleArrayTrie;
import com.hankcs.hanlp.collection.trie.DoubleArrayTrie;
import com.hankcs.hanlp.collection.trie.bintrie.BinTrie;
import com.hankcs.hanlp.corpus.io.ByteArray;
import com.hankcs.hanlp.corpus.io.IOUtil;
import com.hankcs.hanlp.corpus.tag.PosTag;
import com.hankcs.hanlp.dictionary.other.CharTable;
import com.hankcs.hanlp.util.LexiconUtility;
import com.hankcs.hanlp.util.Predefine;
import com.hankcs.hanlp.util.TextUtility;
import com.hankcs.hanlp.dictionary.CoreDictionary.PosTagInfo;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

import static com.hankcs.hanlp.util.Predefine.logger;

/**
 * 用户自定义词典
 *
 * @author He Han
 */
public class CustomDictionary
{
    /**
     * 用于储存用户动态插入词条的二分trie树
     */
    public static BinTrie<PosTagInfo> trie;
    public static DoubleArrayTrie<PosTagInfo> dat = new DoubleArrayTrie<>();
    /**
     * 第一个是主词典，其他是副词典
     */
    public final static String path[] = HanLP.Config.CustomDictionaryPath;

    // 自动加载词典
    static
    {
        long start = System.currentTimeMillis();
        for (String dic : path) {
            if (loadMainDictionary(dic))
                logger.info("自定义词典加载成功:" + dat.size() + "个词条，耗时" + (System.currentTimeMillis() - start) + "ms");
            else
                logger.warning("自定义词典" + Arrays.toString(path) + "加载失败");
        }

//        for (int i=1; i < path.length; i++) {
//            try {
//                Files.lines(Paths.get(path[i])).forEach( line -> {
//                    int startIndex = 0;
//                    while (Character.isSpaceChar(line.charAt(startIndex)))
//                        startIndex++;
//                    int split = line.indexOf(' ', startIndex);
//                    if (split < 0)
//                        add(line.trim());
//                    else
//                        add(line.substring(startIndex, split), line.substring(split).trim());
//                });
//            } catch (IOException e) {
//                logger.warning("自定义词典" + Arrays.toString(path) + "加载失败");
//            }
//        }
    }

    private static boolean loadMainDictionary(String mainPath)
    {
        logger.info("自定义词典开始加载:" + mainPath);
        if (loadDat(mainPath)) return true;
        dat = new DoubleArrayTrie<>();
        TreeMap<String, PosTagInfo> map = new TreeMap<>();
        LinkedHashSet<PosTag> customNatureCollector = new LinkedHashSet<>();
        try
        {
            for (String p : path)
            {
                PosTag defaultNature = PosTag.n;
                int cut = p.indexOf(' ');
                if (cut > 0)
                {
                    // 有默认词性
                    String nature = p.substring(cut + 1);
                    p = p.substring(0, cut);
                    try
                    {
                        defaultNature = LexiconUtility.convertStringToNature(nature, customNatureCollector);
                    }
                    catch (Exception e)
                    {
                        logger.severe("配置文件【" + p + "】写错了！" + e);
                        continue;
                    }
                }
                logger.info("以默认词性[" + defaultNature + "]加载自定义词典" + p + "中……");
                boolean success = load(p, defaultNature, map, customNatureCollector);
                if (!success) logger.warning("失败：" + p);
            }
            if (map.size() == 0)
            {
                logger.warning("没有加载到任何词条");
                map.put(Predefine.TAG_OTHER, null);     // 当作空白占位符
            }
            logger.info("正在构建DoubleArrayTrie……");
            dat.build(map);
            // 缓存成dat文件，下次加载会快很多
            logger.info("正在缓存词典为dat文件……");
            // 缓存值文件
            List<PosTagInfo> attributeList = new LinkedList<>();
            for (Map.Entry<String, PosTagInfo> entry : map.entrySet())
            {
                attributeList.add(entry.getValue());
            }
            DataOutputStream out = new DataOutputStream(IOUtil.newOutputStream(mainPath + Predefine.BIN_EXT));
            // 缓存用户词性
            IOUtil.writeCustomNature(out, customNatureCollector);
            // 缓存正文
            out.writeInt(attributeList.size());
            for (PosTagInfo attribute : attributeList)
            {
                attribute.save(out);
            }
            dat.save(out);
            out.close();
        }
        catch (FileNotFoundException e)
        {
            logger.severe("自定义词典" + mainPath + "不存在！" + e);
            return false;
        }
        catch (IOException e)
        {
            logger.severe("自定义词典" + mainPath + "读取错误！" + e);
            return false;
        }
        catch (Exception e)
        {
            logger.warning("自定义词典" + mainPath + "缓存失败！\n" + TextUtility.exceptionToString(e));
        }
        return true;
    }


    /**
     * 加载用户词典（追加）
     *
     * @param path          词典路径
     * @param defaultNature 默认词性
     * @param customNatureCollector 收集用户词性
     * @return
     */
    public static boolean load(String path, PosTag defaultNature, TreeMap<String, PosTagInfo> map, LinkedHashSet<PosTag> customNatureCollector)
    {
        try
        {
            BufferedReader br = new BufferedReader(new InputStreamReader(IOUtil.newInputStream(path), "UTF-8"));
            String line;
            while ((line = br.readLine()) != null)
            {
                String[] param = line.split("\\s");
                if (param[0].length() == 0) continue;   // 排除空行
                if (HanLP.Config.Normalization) param[0] = CharTable.convert(param[0]); // 正规化

                int natureCount = (param.length - 1) / 2;
                PosTagInfo attribute;
                if (natureCount == 0)
                {
                    attribute = new PosTagInfo(defaultNature);
                }
                else
                {
                    attribute = new PosTagInfo(natureCount);
                    for (int i = 0; i < natureCount; ++i)
                    {
                        attribute.pos[i] = LexiconUtility.convertStringToNature(param[1 + 2 * i], customNatureCollector);
                        attribute.frequency[i] = Integer.parseInt(param[2 + 2 * i]);
                        attribute.totalFrequency += attribute.frequency[i];
                    }
                }
//                if (updateAttributeIfExist(param[0], tagInfo, map, rewriteTable)) continue;
                map.put(param[0], attribute);
            }
            br.close();
        }
        catch (Exception e)
        {
            logger.severe("自定义词典" + path + "读取错误！" + e);
            return false;
        }

        return true;
    }

    /**
     * 如果已经存在该词条,直接更新该词条的属性
     * @param key 词语
     * @param attribute 词语的属性
     * @param map 加载期间的map
     * @param rewriteTable
     * @return 是否更新了
     */
    private static boolean updateAttributeIfExist(String key, PosTagInfo attribute, TreeMap<String, PosTagInfo> map, TreeMap<Integer, PosTagInfo> rewriteTable)
    {
        int wordID = CoreDictionary.getWordID(key);
        PosTagInfo attributeExisted;
        if (wordID != -1)
        {
            attributeExisted = CoreDictionary.get(wordID);
            attributeExisted.pos = attribute.pos;
            attributeExisted.frequency = attribute.frequency;
            attributeExisted.totalFrequency = attribute.totalFrequency;
            // 收集该覆写
            rewriteTable.put(wordID, attribute);
            return true;
        }

        attributeExisted = map.get(key);
        if (attributeExisted != null)
        {
            attributeExisted.pos = attribute.pos;
            attributeExisted.frequency = attribute.frequency;
            attributeExisted.totalFrequency = attribute.totalFrequency;
            return true;
        }

        return false;
    }

    /**
     * 往自定义词典中插入一个新词（非覆盖模式）<br>
     *     动态增删不会持久化到词典文件
     *
     * @param word                新词 如“裸婚”
     * @param posWithFrequency 词性和其对应的频次，比如“nz 1 v 2”，null时表示“nz 1”
     * @return 是否插入成功（失败的原因可能是不覆盖、posWithFrequency有问题等，后者可以通过调试模式了解原因）
     */
    public static boolean add(String word, String posWithFrequency) {
        return !contains(word) && insert(word, posWithFrequency);
    }

    /**
     * 往自定义词典中插入一个新词（非覆盖模式）<br>
     *     动态增删不会持久化到词典文件
     *
     * @param word                新词 如“裸婚”
     * @return 是否插入成功（失败的原因可能是不覆盖等，可以通过调试模式了解原因）
     */
    public static boolean add(String word) {
        if (HanLP.Config.Normalization) word = CharTable.convert(word);
        return !contains(word) && insert(word, null);
    }

    /**
     * 往自定义词典中插入一个新词（覆盖模式）<br>
     *     动态增删不会持久化到词典文件
     *
     * @param word                新词 如“裸婚”
     * @param posWithFrequency 词性和其对应的频次，比如“nz 1 v 2”，null时表示“nz 1”。
     * @return 是否插入成功（失败的原因可能是natureWithFrequency问题，可以通过调试模式了解原因）
     */
    public static boolean insert(String word, String posWithFrequency)
    {
        if (word == null) return false;
        if (HanLP.Config.Normalization) word = CharTable.convert(word);
        PosTagInfo att = posWithFrequency == null ? new PosTagInfo(PosTag.nz, 1) : PosTagInfo.create(posWithFrequency);
        if (att == null) return false;
        if (dat.set(word, att)) return true;
        if (trie == null) trie = new BinTrie<>();
        trie.put(word, att);
        return true;
    }

    /**
     * 以覆盖模式增加新词<br>
     *     动态增删不会持久化到词典文件
     *
     * @param word
     * @return
     */
    public static boolean insert(String word)
    {
        return insert(word, null);
    }

    /**
     * 从磁盘加载双数组
     *
     * @param path
     * @return
     */
    private static boolean loadDat(String path)
    {
        try
        {
            ByteArray byteArray = ByteArray.createByteArray(path + Predefine.BIN_EXT);
            if (byteArray == null) return false;
            int size = byteArray.nextInt();
            if (size < 0)   // 一种兼容措施,当size小于零表示文件头部储存了-size个用户词性
            {
                while (++size <= 0)
                {
                    PosTag.create(byteArray.nextString());
                }
                size = byteArray.nextInt();
            }
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
            if (!dat.load(byteArray, attributes)) return false;
        }
        catch (Exception e)
        {
            logger.warning("读取失败，问题发生在" + TextUtility.exceptionToString(e));
            return false;
        }
        return true;
    }

    /**
     * 查单词
     *
     * @param key
     * @return
     */
    public static PosTagInfo get(String key)
    {
        if (HanLP.Config.Normalization) key = CharTable.convert(key);
        PosTagInfo attribute = dat.get(key);
        if (attribute != null) return attribute;
        if (trie == null) return null;
        return trie.get(key);
    }

    /**
     * 删除单词<br>
     *     动态增删不会持久化到词典文件
     *
     * @param key
     */
    public static void remove(String key)
    {
        if (HanLP.Config.Normalization) key = CharTable.convert(key);
        if (trie == null) return;
        trie.remove(key);
    }

    /**
     * 前缀查询
     *
     * @param key
     * @return
     */
    public static LinkedList<Map.Entry<String, PosTagInfo>> commonPrefixSearch(String key)
    {
        return trie.commonPrefixSearchWithValue(key);
    }

    /**
     * 前缀查询
     *
     * @param chars
     * @param begin
     * @return
     */
    public static LinkedList<Map.Entry<String, PosTagInfo>> commonPrefixSearch(char[] chars, int begin)
    {
        return trie.commonPrefixSearchWithValue(chars, begin);
    }

    public static BaseSearcher getSearcher(String text)
    {
        return new Searcher(text);
    }

    @Override
    public String toString()
    {
        return "CustomDictionary{" +
                "trie=" + trie +
                '}';
    }

    /**
     * 词典中是否含有词语
     * @param key 词语
     * @return 是否包含
     */
    public static boolean contains(String key) {
        return dat.exactMatchSearch(key) >= 0 || trie != null && trie.containsKey(key);
    }

    /**
     * 获取一个BinTrie的查询工具
     * @param charArray 文本
     * @return 查询者
     */
    public static BaseSearcher getSearcher(char[] charArray)
    {
        return new Searcher(charArray);
    }

    static class Searcher extends BaseSearcher<PosTagInfo>
    {
        /**
         * 分词从何处开始，这是一个状态
         */
        int begin;

        private LinkedList<Map.Entry<String, PosTagInfo>> entryList;

        protected Searcher(char[] c)
        {
            super(c);
            entryList = new LinkedList<>();
        }

        protected Searcher(String text)
        {
            super(text);
            entryList = new LinkedList<>();
        }

        @Override
        public Map.Entry<String, PosTagInfo> next()
        {
            // 保证首次调用找到一个词语
            while (entryList.size() == 0 && begin < c.length)
            {
                entryList = trie.commonPrefixSearchWithValue(c, begin);
                ++begin;
            }
            // 之后调用仅在缓存用完的时候调用一次
            if (entryList.size() == 0 && begin < c.length)
            {
                entryList = trie.commonPrefixSearchWithValue(c, begin);
                ++begin;
            }
            if (entryList.size() == 0)
            {
                return null;
            }
            Map.Entry<String, PosTagInfo> result = entryList.getFirst();
            entryList.removeFirst();
            offset = begin - 1;
            return result;
        }
    }

    /**
     * 获取词典对应的trie树
     *
     * @return
     * @deprecated 谨慎操作，有可能废弃此接口
     */
    public static BinTrie<PosTagInfo> getTrie()
    {
        return trie;
    }

    /**
     * 解析一段文本（目前采用了BinTrie+DAT的混合储存形式，此方法可以统一两个数据结构）
     * @param text         文本
     * @param processor    处理器
     */
    public static void parseText(char[] text, AhoCorasickDoubleArrayTrie.IHit<PosTagInfo> processor)
    {
        if (trie != null)
        {
            BaseSearcher searcher = CustomDictionary.getSearcher(text);
            int offset;
            Map.Entry<String, PosTagInfo> entry;
            while ((entry = searcher.next()) != null)
            {
                offset = searcher.getOffset();
                processor.hit(offset, offset + entry.getKey().length(), entry.getValue());
            }
        }
        DoubleArrayTrie<PosTagInfo>.Searcher searcher = dat.getSearcher(text, 0);
        while (searcher.next())
        {
            processor.hit(searcher.begin, searcher.begin + searcher.length, searcher.value);
        }
    }
}
